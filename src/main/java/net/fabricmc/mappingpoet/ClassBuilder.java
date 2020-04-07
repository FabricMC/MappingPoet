package net.fabricmc.mappingpoet;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassBuilder {

	private final MappingsStore mappings;
	private final ClassNode classNode;

	private final TypeSpec.Builder builder;
	private final List<ClassBuilder> innerClasses = new ArrayList<>();
	
	private Signatures.ClassSignature signature;

	public ClassBuilder(MappingsStore mappings, ClassNode classNode) {
		this.mappings = mappings;
		this.classNode = classNode;
		this.builder = setupBuilder();
		addInterfaces();
		addMethods();
		addFields();
		addJavaDoc();
	}

	private TypeSpec.Builder setupBuilder() {
		if (classNode.signature != null) {
			signature = Signatures.parseClassSignature(classNode.signature);
		}
		TypeSpec.Builder builder;
		if (Modifier.isInterface(classNode.access)) {
			builder = TypeSpec.interfaceBuilder(getClassName(classNode.name));
		} else if (classNode.superName.equals("java/lang/Enum")) {
			builder = TypeSpec.enumBuilder(getClassName(classNode.name));
		} else {
			builder = TypeSpec.classBuilder(getClassName(classNode.name))
					.superclass(signature == null ? getClassName(classNode.superName) : signature.superclass);
		}
		
		if (signature != null && signature.generics != null) {
			builder.addTypeVariables(signature.generics);
		}

		return builder
				.addModifiers(new ModifierBuilder(classNode.access).getModifiers(ModifierBuilder.Type.CLASS));
	}

	private void addInterfaces() {
		if (signature != null) {
			builder.addSuperinterfaces(signature.superinterfaces);
			return;
		}
		if (classNode.interfaces == null) return;

		for (String iFace :classNode.interfaces){
			builder.addSuperinterface(getClassName(iFace));
		}
	}
	
	private void addMethods() {
		if (classNode.methods == null) return;
		for (MethodNode method : classNode.methods) {
			if ((method.access & Opcodes.ACC_SYNTHETIC) != 0) {
				continue;
			}
			if (method.name.equals("<clinit>")) {
				continue;
			}
			builder.addMethod(new MethodBuilder(mappings, classNode, method).build());
		}
	}

	private void addFields() {
		if (classNode.fields == null) return;
		for (FieldNode field : classNode.fields) {
			if ((field.access & Opcodes.ACC_SYNTHETIC) != 0)
				continue; // hide synthetic stuff
			if ((field.access & Opcodes.ACC_ENUM)  == 0) {
				builder.addField(new FieldBuilder(mappings, classNode, field).build());
			} else {
				TypeSpec.Builder enumBuilder = TypeSpec.anonymousClassBuilder("");
				FieldBuilder.addFieldJavaDoc(enumBuilder, mappings, classNode, field);
				builder.addEnumConstant(field.name, enumBuilder.build());
			}
		}
	}
	
	private void addJavaDoc() {
		String javadoc = mappings.getClassDoc(classNode.name);
		if (javadoc != null) {
			builder.addJavadoc(javadoc);
		}
	}

	public void addInnerClass(ClassBuilder classBuilder) {
		classBuilder.builder.addModifiers(javax.lang.model.element.Modifier.PUBLIC);
		classBuilder.builder.addModifiers(javax.lang.model.element.Modifier.STATIC);
		innerClasses.add(classBuilder);
	}

	public String getClassName() {
		return classNode.name;
	}

	public TypeSpec build() {
		innerClasses.stream()
				.map(ClassBuilder::build)
				.forEach(builder::addType);
		return builder.build();
	}
	
	public static ClassName parseInternalName(String internalName) {
		int classNameSeparator = -1;
		int index = 0;
		int nameStart = index;
		ClassName currentClassName = null;

		char ch;
		do {
			ch = index == internalName.length() ? ';' : internalName.charAt(index);

			if (ch == '$' || ch == ';') {
				// collect class name
				if (currentClassName == null) {
					String packageName = nameStart < classNameSeparator ? internalName.substring(nameStart, classNameSeparator).replace('/', '.') : "";
					String simpleName = internalName.substring(classNameSeparator + 1, index);
					currentClassName = ClassName.get(packageName, simpleName);
				} else {
					String simpleName = internalName.substring(classNameSeparator + 1, index);
					currentClassName = currentClassName.nestedClass(simpleName);
				}
			}

			if (ch == '/' || ch == '$') {
				// Start of simple name
				classNameSeparator = index;
			}

			index++;
		} while (ch != ';');

		if (currentClassName == null)
			throw new IllegalArgumentException(String.format("Invalid internal name \"%s\"", internalName));
		
		return currentClassName;
	}

	@Deprecated // Use parseInternalName, less allocations
	public static ClassName getClassName(String input) {
		int lastDelim = input.lastIndexOf("/");
		String packageName = input.substring(0, lastDelim).replaceAll("/", ".");
		String className = input.substring(lastDelim + 1).replaceAll("/", ".");

		List<String> classSplit = new ArrayList<>(Arrays.asList(className.split("\\$")));
		String parentClass = classSplit.get(0);
		classSplit.remove(0);

		return ClassName.get(packageName, parentClass, classSplit.toArray(new String[]{}));
	}
}
