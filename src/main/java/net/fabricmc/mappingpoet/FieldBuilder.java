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

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import net.fabricmc.mapping.util.EntryTriple;
import net.fabricmc.mappingpoet.signature.AnnotationAwareDescriptors;
import net.fabricmc.mappingpoet.signature.AnnotationAwareSignatures;
import net.fabricmc.mappingpoet.signature.ClassStaticContext;
import net.fabricmc.mappingpoet.signature.TypeAnnotationBank;
import net.fabricmc.mappingpoet.signature.TypeAnnotationMapping;
import net.fabricmc.mappingpoet.signature.TypeAnnotationStorage;

public class FieldBuilder {
	private final MappingsStore mappings;
	private final ClassNode classNode;
	private final FieldNode fieldNode;
	private final FieldSpec.Builder builder;
	private final TypeAnnotationMapping annotations;
	private final ClassStaticContext context;

	public FieldBuilder(MappingsStore mappings, ClassNode classNode, FieldNode fieldNode, ClassStaticContext context) {
		this.mappings = mappings;
		this.classNode = classNode;
		this.fieldNode = fieldNode;
		this.context = context;
		this.annotations = TypeAnnotationStorage.builder()
				.add(fieldNode.invisibleTypeAnnotations)
				.add(fieldNode.visibleTypeAnnotations)
				.build();
		this.builder = createBuilder();
		addDirectAnnotations();
		addJavaDoc();
	}

	static void addFieldJavaDoc(TypeSpec.Builder enumBuilder, MappingsStore mappings, ClassNode classNode, FieldNode fieldNode) {
		String javaDoc = mappings.getFieldDoc(new EntryTriple(classNode.name, fieldNode.name, fieldNode.desc));
		if (javaDoc != null) {
			enumBuilder.addJavadoc(javaDoc);
		}
	}

	public static AnnotationSpec parseAnnotation(AnnotationNode annotation) {
		ClassName annoClassName = (ClassName) typeFromDesc(annotation.desc);
		AnnotationSpec.Builder builder = AnnotationSpec.builder(annoClassName);
		List<Object> values = annotation.values;
		if (values != null) {
			Iterator<?> itr = values.iterator();
			while (itr.hasNext()) {
				String key = (String) itr.next();
				Object value = itr.next();

				builder.addMember(key, codeFromAnnoValue(value));
			}
		}

		return builder.build();
	}

	public static CodeBlock codeFromAnnoValue(Object value) {
		// BCDFIJSZ; String; String[] (for enum); asm type; anno node; list of any prev stuff (cannot nest)
		if (value instanceof List) {
			return ((List<?>) value).stream().map(FieldBuilder::codeFromAnnoValue).collect(CodeBlock.joining(",", "{", "}"));
		}
		if (value instanceof Character || value instanceof Number || value instanceof Boolean) {
			return CodeBlock.builder().add("$L", value).build();
		}
		if (value instanceof String) {
			return CodeBlock.builder().add("$S", value).build();
		}
		if (value instanceof String[]) {
			String[] arr = (String[]) value;
			ClassName enumClassName = (ClassName) typeFromDesc(arr[0]);
			String valueName = arr[1];
			return CodeBlock.builder().add("$T.$L", enumClassName, valueName).build();
		}
		if (value instanceof Type) {
			return CodeBlock.builder().add("$T.class", typeFromDesc(((Type) value).getDescriptor())).build();
		}
		if (value instanceof AnnotationNode) {
			return CodeBlock.builder().add(parseAnnotation((AnnotationNode) value).toString()).build();
		}
		throw new IllegalArgumentException(String.format("Don't know how to convert \"%s\" into annotation value", value));
	}

	public static TypeName typeFromDesc(final String desc) {
		return parseType(desc, 0).getValue();
	}

	public static Map.Entry<Integer, TypeName> parseType(final String desc, final int start) {
		int index = start;
		int arrayLevel = 0;
		while (desc.charAt(index) == '[') {
			arrayLevel++;
			index++;
		}

		TypeName current;
		switch (desc.charAt(index)) {
		case 'B': {
			current = TypeName.BYTE;
			index++;
			break;
		}
		case 'C': {
			current = TypeName.CHAR;
			index++;
			break;
		}
		case 'D': {
			current = TypeName.DOUBLE;
			index++;
			break;
		}
		case 'F': {
			current = TypeName.FLOAT;
			index++;
			break;
		}
		case 'I': {
			current = TypeName.INT;
			index++;
			break;
		}
		case 'J': {
			current = TypeName.LONG;
			index++;
			break;
		}
		case 'S': {
			current = TypeName.SHORT;
			index++;
			break;
		}
		case 'Z': {
			current = TypeName.BOOLEAN;
			index++;
			break;
		}
		case 'V': {
			current = TypeName.VOID;
			index++;
			break;
		}
		case 'L': {
			int classNameSeparator = index;
			index++;
			int nameStart = index;
			ClassName currentClassName = null;

			char ch;
			do {
				ch = desc.charAt(index);

				if (ch == '$' || ch == ';') {
					// collect class name
					if (currentClassName == null) {
						String packageName = nameStart < classNameSeparator ? desc.substring(nameStart, classNameSeparator).replace('/', '.') : "";
						String simpleName = desc.substring(classNameSeparator + 1, index);
						currentClassName = ClassName.get(packageName, simpleName);
					} else {
						String simpleName = desc.substring(classNameSeparator + 1, index);
						currentClassName = currentClassName.nestedClass(simpleName);
					}
				}

				if (ch == '/' || ch == '$') {
					// Start of simple name
					classNameSeparator = index;
				}

				index++;
			} while (ch != ';');

			if (currentClassName == null) {
				throw invalidDesc(desc, index);
			}

			current = currentClassName;
			break;
		}
		default:
			throw invalidDesc(desc, index);
		}

		for (int i = 0; i < arrayLevel; i++) {
			current = ArrayTypeName.of(current);
		}

		return new AbstractMap.SimpleImmutableEntry<>(index, current);
	}

	public static Map.Entry<Integer, TypeName> parseAnnotatedType(final String desc, final int start, TypeAnnotationBank annotations, ClassStaticContext context) {
		int index = start;
		Deque<List<AnnotationSpec>> arrayAnnos = new ArrayDeque<>();
		while (desc.charAt(index) == '[') {
			arrayAnnos.push(annotations.getCurrentAnnotations());
			annotations = annotations.advance(TypePath.ARRAY_ELEMENT, 0);
			index++;
		}

		TypeName current;
		switch (desc.charAt(index)) {
		case 'B': {
			current = TypeName.BYTE;
			index++;
			break;
		}
		case 'C': {
			current = TypeName.CHAR;
			index++;
			break;
		}
		case 'D': {
			current = TypeName.DOUBLE;
			index++;
			break;
		}
		case 'F': {
			current = TypeName.FLOAT;
			index++;
			break;
		}
		case 'I': {
			current = TypeName.INT;
			index++;
			break;
		}
		case 'J': {
			current = TypeName.LONG;
			index++;
			break;
		}
		case 'S': {
			current = TypeName.SHORT;
			index++;
			break;
		}
		case 'Z': {
			current = TypeName.BOOLEAN;
			index++;
			break;
		}
		case 'V': {
			current = TypeName.VOID;
			index++;
			break;
		}
		case 'L': {
			int classNameSeparator = index;
			index++;
			int nameStart = index;
			ClassName currentClassName = null;
			boolean instanceInner = false;

			char ch;
			do {
				ch = desc.charAt(index);

				if (ch == '$' || ch == ';') {
					// collect class name
					if (currentClassName == null) {
						String packageName = nameStart < classNameSeparator ? desc.substring(nameStart, classNameSeparator).replace('/', '.') : "";
						String simpleName = desc.substring(classNameSeparator + 1, index);
						currentClassName = ClassName.get(packageName, simpleName);
					} else {
						String simpleName = desc.substring(classNameSeparator + 1, index);

						if (!instanceInner && context.isInstanceInner(desc.substring(nameStart, index))) {
							instanceInner = true;
						}

						currentClassName = currentClassName.nestedClass(simpleName);

						if (instanceInner) {
							currentClassName = AnnotationAwareDescriptors.annotate(currentClassName, annotations);
							annotations = annotations.advance(TypePath.INNER_TYPE, 0);
						}
					}
				}

				if (ch == '/' || ch == '$') {
					// Start of simple name
					classNameSeparator = index;
				}

				index++;
			} while (ch != ';');

			if (currentClassName == null) {
				throw invalidDesc(desc, index);
			}

			current = currentClassName;
			break;
		}
		default:
			throw invalidDesc(desc, index);
		}

		while (!arrayAnnos.isEmpty()) {
			current = ArrayTypeName.of(current);
			List<AnnotationSpec> currentAnnos = arrayAnnos.pop();
			if (!currentAnnos.isEmpty()) {
				current = current.annotated(currentAnnos);
			}
		}

		return new AbstractMap.SimpleImmutableEntry<>(index, current);
	}

	private static IllegalArgumentException invalidDesc(String desc, int index) {
		return new IllegalArgumentException(String.format("Invalid descriptor at index %d for \"%s\"", index, desc));
	}

	@Deprecated // use typeFromDesc, non-recursive
	public static TypeName getFieldType(String desc) {
		switch (desc) {
		case "B":
			return TypeName.BYTE;
		case "C":
			return TypeName.CHAR;
		case "S":
			return TypeName.SHORT;
		case "Z":
			return TypeName.BOOLEAN;
		case "I":
			return TypeName.INT;
		case "J":
			return TypeName.LONG;
		case "F":
			return TypeName.FLOAT;
		case "D":
			return TypeName.DOUBLE;
		case "V":
			return TypeName.VOID;
		}
		if (desc.startsWith("[")) {
			return ArrayTypeName.of(getFieldType(desc.substring(1)));
		}
		if (desc.startsWith("L")) {
			return ClassBuilder.parseInternalName(desc.substring(1).substring(0, desc.length() - 2));
		}
		throw new UnsupportedOperationException("Unknown field type" + desc);
	}

	private FieldSpec.Builder createBuilder() {
		FieldSpec.Builder ret = FieldSpec.builder(calculateType(), fieldNode.name)
				.addModifiers(new ModifierBuilder(fieldNode.access).getModifiers(ModifierBuilder.Type.FIELD));

		if ((fieldNode.access & Opcodes.ACC_FINAL) != 0) {
			ret.initializer(makeInitializer(fieldNode.desc)); // so jd doesn't complain about type mismatch
		}

		return ret;
	}

	private CodeBlock makeInitializer(String desc) {
		// fake initializers exclude fields from constant values
		switch (desc.charAt(0)) {
		case 'B':
		case 'C':
		case 'D':
		case 'I':
		case 'J':
		case 'S':
			if (fieldNode.value != null) {
				return CodeBlock.builder().add(String.valueOf(fieldNode.value)).build();
			}
			return CodeBlock.builder().add("java.lang.Byte.parseByte(\"dummy\")").build();
		case 'F':
			if (fieldNode.value != null) {
				return CodeBlock.builder().add(fieldNode.value + "f").build();
			}
			return CodeBlock.builder().add("java.lang.Float.parseFloat(\"dummy\")").build();
		case 'Z':
			if (fieldNode.value instanceof Number) {
				return CodeBlock.builder().add(Boolean.toString(((Number) fieldNode.value).intValue() != 0)).build();
			}
			return CodeBlock.builder().add("java.lang.Boolean.parseBoolean(\"dummy\")").build();
		}
		if (fieldNode.value != null) {
			return CodeBlock.builder().add("$S", fieldNode.value).build();
		}
		return CodeBlock.builder().add(desc.equals("Ljava/lang/String;") ? "java.lang.String.valueOf(\"dummy\")" : "null").build();
	}

	private void addJavaDoc() {
		String javaDoc = mappings.getFieldDoc(new EntryTriple(classNode.name, fieldNode.name, fieldNode.desc));
		if (javaDoc != null) {
			builder.addJavadoc(javaDoc);
		}
	}

	private void addDirectAnnotations() {
		addDirectAnnotations(fieldNode.invisibleAnnotations);
		addDirectAnnotations(fieldNode.visibleAnnotations);
	}

	private void addDirectAnnotations(List<AnnotationNode> regularAnnotations) {
		if (regularAnnotations == null) {
			return;
		}
		for (AnnotationNode annotation : regularAnnotations) {
			builder.addAnnotation(parseAnnotation(annotation));
		}
	}

	private TypeName calculateType() {
		if (fieldNode.signature != null) {
			return AnnotationAwareSignatures.parseFieldSignature(fieldNode.signature, annotations, context);
		}
		return parseAnnotatedType(fieldNode.desc, 0, annotations.getBank(TypeReference.newTypeReference(TypeReference.FIELD)), context).getValue();
	}

	public FieldSpec build() {
		return builder.build();
	}
}
