/*
 * Copyright (c) 2020 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.fabricmc.mappingpoet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.squareup.javapoet.JavaFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import net.fabricmc.mappingpoet.Environment.ClassNamePointer;
import net.fabricmc.mappingpoet.Environment.NestedClassInfo;

public class Main {

	public static void main(String[] args) {
		if (args.length != 3 && args.length != 4) {
			System.out.println("<mappings> <inputJar> <outputDir> [<librariesDir>]");
			return;
		}
		Path mappings = Paths.get(args[0]);
		Path inputJar = Paths.get(args[1]);
		Path outputDirectory = Paths.get(args[2]);
		Path librariesDir = args.length < 4 ? null : Paths.get(args[3]);

		try {
			if (Files.exists(outputDirectory)) {
				try (var stream = Files.walk(outputDirectory)) {
					stream.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
				}
			}

			Files.createDirectories(outputDirectory);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		if (!Files.exists(mappings)) {
			System.out.println("could not find mappings");
			return;
		}

		if (!Files.exists(inputJar)) {
			System.out.println("could not find input jar");
			return;
		}

		generate(mappings, inputJar, outputDirectory, librariesDir);
	}

	public static void generate(Path mappings, Path inputJar, Path outputDirectory, Path librariesDir) {
		final MappingsStore mapping = new MappingsStore(mappings);
		Map<String, ClassBuilder> classes = new HashMap<>();
		forEachClass(inputJar, (classNode, environment) -> writeClass(mapping, classNode, classes, environment), librariesDir);

		for (ClassBuilder classBuilder : classes.values()) {
			String name = classBuilder.getClassName();
			if (name.contains("$")) continue;

			try {
				int packageEnd = classBuilder.getClassName().lastIndexOf("/");
				String pkgName = packageEnd < 0 ? "" : classBuilder.getClassName().substring(0, packageEnd).replaceAll("/", ".");
				JavaFile javaFile = JavaFile.builder(pkgName, classBuilder.build()).build();

				javaFile.writeTo(outputDirectory);
			} catch (Throwable t) {
				throw new RuntimeException("Failed to process class "+name, t);
			}
		}
	}

	private static void forEachClass(Path jar, ClassNodeConsumer classNodeConsumer, Path librariesDir) {
		List<ClassNode> classes = new ArrayList<>();
		Map<String, Collection<String>> supers = new HashMap<>();
		Set<String> sealedClasses = new HashSet<>(); // their subclsses/impls need non-sealed modifier

		Map<String, Environment.NestedClassInfo> nestedClasses = new ConcurrentHashMap<>();
		Map<String, ClassNamePointer> classNames = new ConcurrentHashMap<>();

		if (librariesDir != null) {
			scanNestedClasses(classNames, nestedClasses, librariesDir);
		}

		try (final JarFile jarFile = new JarFile(jar.toFile())) {
			Enumeration<JarEntry> entryEnumerator = jarFile.entries();

			while (entryEnumerator.hasMoreElements()) {
				JarEntry entry = entryEnumerator.nextElement();

				if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
					continue;
				}

				try (InputStream is = jarFile.getInputStream(entry)) {
					ClassReader reader = new ClassReader(is);
					ClassNode classNode = new ClassNode();
					reader.accept(classNode, ClassReader.SKIP_CODE);
					List<String> superNames = new ArrayList<>();
					if (classNode.superName != null && !classNode.superName.equals("java/lang/Object")) {
						superNames.add(classNode.superName);
					}
					if (classNode.interfaces != null) {
						superNames.addAll(classNode.interfaces);
					}
					if (!superNames.isEmpty()) {
						supers.put(classNode.name, superNames);
					}

					if (classNode.innerClasses != null) {
						for (InnerClassNode e : classNode.innerClasses) {
							if (e.outerName != null) {
								// null -> declared in method/initializer
								nestedClasses.put(e.name, new NestedClassInfo(e.outerName, !Modifier.isStatic(e.access), e.innerName));
							}
						}
					}

					if (classNode.permittedSubclasses != null) {
						sealedClasses.add(classNode.name);
					}

					classes.add(classNode);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		//Sort all the classes making sure that inner classes come after the parent classes
		classes.sort(Comparator.comparing(o -> o.name));

		for (ClassNode node : classes) {
			classNodeConsumer.accept(node, new Environment(supers, sealedClasses, nestedClasses));
		}
	}

	private static void scanNestedClasses(Map<String, ClassNamePointer> classNames, Map<String, Environment.NestedClassInfo> instanceInnerClasses, Path librariesDir) {
		try {
			Files.walkFileTree(librariesDir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (!file.getFileName().toString().endsWith(".jar")) {
						return FileVisitResult.CONTINUE;
					}

					try (final JarFile jarFile = new JarFile(file.toFile())) {
						Enumeration<JarEntry> entryEnumerator = jarFile.entries();

						while (entryEnumerator.hasMoreElements()) {
							JarEntry entry = entryEnumerator.nextElement();

							if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
								continue;
							}

							try (InputStream is = jarFile.getInputStream(entry)) {
								ClassReader reader = new ClassReader(is);
								reader.accept(new ClassVisitor(Opcodes.ASM9) {
									@Override
									public void visitInnerClass(String name, String outerName, String simpleName, int access) {
										instanceInnerClasses.put(name, new Environment.NestedClassInfo(outerName, !Modifier.isStatic(access), simpleName));
										if (outerName != null) {
											classNames.put(name, new ClassNamePointer(simpleName, outerName));
										}
									}
								}, ClassReader.SKIP_CODE);
							}
						}
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static boolean isInstanceInnerOnClasspath(String internalName) {
		String javaBinary = internalName.replace('/', '.');

		try {
			Class<?> c = Class.forName(javaBinary, false, Main.class.getClassLoader());
			return !Modifier.isStatic(c.getModifiers()) && c.getDeclaringClass() != null;
		} catch (Throwable ex) {
			return false;
		}
	}

	private static boolean isDigit(char ch) {
		return ch >= '0' && ch <= '9';
	}

	private static void writeClass(MappingsStore mappings, ClassNode classNode, Map<String, ClassBuilder> existingClasses, Environment environment) {
		if ((classNode.access & Opcodes.ACC_SYNTHETIC) != 0) {
			// Skip synthetic classes such as package-info
			return;
		}

		// TODO make sure named jar has valid InnerClasses, use that info instead
		String name = classNode.name;
		{
			//Block anonymous class and their nested classes
			int lastSearch = name.length();
			while (lastSearch != -1) {
				lastSearch = name.lastIndexOf('$', lastSearch - 1);
				// names starting with digit is illegal java
				if (isDigit(name.charAt(lastSearch + 1))) {
					return;
				}
			}
		}

		// TODO: ensure InnerClasses is remapped, and create ClassName from parent class name
		ClassBuilder classBuilder = new ClassBuilder(mappings, classNode, environment);

		if (name.contains("$")) {
			String parentClass = name.substring(0, name.lastIndexOf("$"));
			if (!existingClasses.containsKey(parentClass)) {
				throw new RuntimeException("Could not find parent class: " + parentClass + " for " + classNode.name);
			}
			existingClasses.get(parentClass).addInnerClass(classBuilder);
		}

		classBuilder.addMembers();
		existingClasses.put(name, classBuilder);

	}

	@FunctionalInterface
	private interface ClassNodeConsumer {
		void accept(ClassNode node, Environment environment);
	}
}
