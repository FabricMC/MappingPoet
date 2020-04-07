package net.fabricmc.mappingpoet;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mappings.EntryTriple;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

//Taken from loom
public class MappingsStore {
	private final Map<String, ClassDef> classes = new HashMap<>();
	private final Map<EntryTriple, FieldDef> fields = new HashMap<>();
	private final Map<EntryTriple, MethodDef> methods = new HashMap<>();

	private final String namespace = "named";

	public MappingsStore(Path tinyFile) {
		final TinyTree mappings = readMappings(tinyFile);

		for (ClassDef classDef : mappings.getClasses()) {
			final String className = classDef.getName(namespace);
			classes.put(className, classDef);

			for (FieldDef fieldDef : classDef.getFields()) {
				fields.put(new EntryTriple(className, fieldDef.getName(namespace), fieldDef.getDescriptor(namespace)), fieldDef);
			}

			for (MethodDef methodDef : classDef.getMethods()) {
				methods.put(new EntryTriple(className, methodDef.getName(namespace), methodDef.getDescriptor(namespace)), methodDef);
			}
		}
	}

	public String getClassDoc(String className) {
		ClassDef classDef = classes.get(className);
		return classDef != null ? classDef.getComment() : null;
	}

	public String getFieldDoc(EntryTriple fieldEntry) {
		FieldDef fieldDef = fields.get(fieldEntry);
		return fieldDef != null ? fieldDef.getComment() : null;
	}

	public Map.Entry<String, String> getParamNameAndDoc(EntryTriple methodEntry, int index) {
		MethodDef methodDef = methods.get(methodEntry);
		if (methodDef != null) {
			if (methodDef.getParameters().isEmpty()) {
				return null;
			}
			return methodDef.getParameters().stream()
					.filter(param -> param.getLocalVariableIndex() == index)
					.map(param -> new AbstractMap.SimpleImmutableEntry<>(param.getName(namespace), param.getComment()))
					.findFirst()
					.orElse(null);
		}
		return null;
	}

	public String getMethodDoc(EntryTriple methodEntry) {
		MethodDef methodDef = methods.get(methodEntry);

		if (methodDef != null) {
			return methodDef.getComment(); // comment doc handled separately by javapoet
		}

		return null;
	}

	private static TinyTree readMappings(Path input) {
		try (BufferedReader reader = Files.newBufferedReader(input)) {
			return TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}
	}
}
