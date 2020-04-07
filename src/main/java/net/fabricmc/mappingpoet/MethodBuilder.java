package net.fabricmc.mappingpoet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.mappings.EntryTriple;

public class MethodBuilder {
	private static final Set<String> RESERVED_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
			"default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
			"implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
			"protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
			"throw", "throws", "transient", "try", "void", "volatile", "while"
	)));
	private final MappingsStore mappings;
	private final ClassNode classNode;
	private final MethodNode methodNode;
	private final MethodSpec.Builder builder;

	private Signatures.MethodSignature signature;

	public MethodBuilder(MappingsStore mappings, ClassNode classNode, MethodNode methodNode) {
		this.mappings = mappings;
		this.classNode = classNode;
		this.methodNode = methodNode;
		this.builder = createBuilder();
		addJavaDoc();
		setReturnType();
		addParameters();
		addExceptions();
	}

	private MethodSpec.Builder createBuilder() {
		MethodSpec.Builder builder = MethodSpec.methodBuilder(methodNode.name)
				.addModifiers(new ModifierBuilder(methodNode.access).getModifiers(ModifierBuilder.Type.METHOD));
		if (methodNode.name.equals("<init>") || !java.lang.reflect.Modifier.isInterface(classNode.access)) {
			builder.modifiers.remove(Modifier.DEFAULT);
		}

		if (methodNode.signature != null) {
			signature = Signatures.parseMethodSignature(methodNode.signature);
			if (signature.generics != null) {
				builder.addTypeVariables(signature.generics);
			}
		}

		return builder;
	}

	private void setReturnType() {
		//Skip constructors
		if (methodNode.name.equals("<init>")) {
			return;
		}

		TypeName typeName;
		if (signature != null) {
			typeName = signature.result;
		} else {
			String returnDesc = methodNode.desc.substring(methodNode.desc.lastIndexOf(")") + 1);
			typeName = FieldBuilder.typeFromDesc(returnDesc);
		}

		builder.returns(typeName);
		if (typeName != TypeName.VOID && !builder.modifiers.contains(Modifier.ABSTRACT)) {
			builder.addStatement("throw new RuntimeException()");
		}
	}

	private void addParameters() {
		// todo fix enum ctors
		List<ParamType> paramTypes = new ArrayList<>();
		boolean instanceMethod = !builder.modifiers.contains(Modifier.STATIC);
		Set<String> usedParamNames = new HashSet<>(RESERVED_KEYWORDS);
		if (signature != null) {
			getGenericParams(paramTypes, instanceMethod, usedParamNames);
		} else {
			getDescParams(paramTypes, instanceMethod, usedParamNames);
		}
		for (ParamType paramType : paramTypes) {
			paramType.fillName(usedParamNames);
			ParameterSpec.Builder paramBuilder = ParameterSpec.builder(paramType.type, paramType.name, paramType.modifiers);
			if (paramType.comment != null) {
				paramBuilder.addJavadoc(paramType.comment + "\n");
			}
			builder.addParameter(paramBuilder.build());
		}
	}

	private void getGenericParams(List<ParamType> paramTypes, boolean instance, Set<String> usedParamNames) {
		int slot = instance ? 1 : 0;
		for (TypeName each : signature.parameters) {
			paramTypes.add(new ParamType(mappings.getParamNameAndDoc(new EntryTriple(classNode.name, methodNode.name, methodNode.desc), slot), each, usedParamNames, slot));
			slot++;
			if (each.equals(TypeName.DOUBLE) || each.equals(TypeName.LONG)) {
				slot++;
			}
		}
	}

	private void getDescParams(List<ParamType> paramTypes, boolean instance, Set<String> usedParamNames) {
		int slot = instance ? 1 : 0;
		final String desc = methodNode.desc;
		int index = 0;

		if (desc.charAt(index) != '(') {
			throw invalidMethodDesc(desc, index);
		}
		index++; // consume '('

		while (desc.charAt(index) != ')') {
			Map.Entry<Integer, TypeName> parsedParam = FieldBuilder.parseType(desc, index);
			index = parsedParam.getKey();
			TypeName paramType = parsedParam.getValue();

			paramTypes.add(new ParamType(mappings.getParamNameAndDoc(new EntryTriple(classNode.name, methodNode.name, methodNode.desc), slot), paramType, usedParamNames, slot));
			slot++;
			if (paramType.equals(TypeName.DOUBLE) || paramType.equals(TypeName.LONG)) {
				slot++;
			}
		}
		/* bruh, we don't care about return type
		index++; // consume ')'
		Map.Entry<Integer, TypeName> parsedReturn = FieldBuilder.parseType(desc, index);
		index = parsedReturn.getKey();
		TypeName returnType = parsedReturn.getValue();
		 */
	}

	private static IllegalArgumentException invalidMethodDesc(String desc, int index) {
		return new IllegalArgumentException(String.format("Invalid method descriptor at %d: \"%s\"", index, desc));
	}

	static String reserveValidName(String suggestedName, Set<String> usedNames) {
		if (!usedNames.contains(suggestedName)) {
			usedNames.add(suggestedName);
			return suggestedName;
		}
		int t = 2;
		String currentSuggestion = suggestedName + t;
		while (usedNames.contains(currentSuggestion)) {
			t++;
			currentSuggestion = suggestedName + t;
		}
		
		usedNames.add(currentSuggestion);

		return currentSuggestion;
	}

	static String suggestName(TypeName type) {
		String str = type.toString();
		int newStart = 0;
		int newEnd = str.length();
		int ltStart;
		ltStart = str.indexOf('<', newStart);
		if (ltStart != -1 && ltStart < newEnd) {
			newEnd = ltStart;
		}
		ltStart = str.indexOf('[', newStart);
		if (ltStart != -1 && ltStart < newEnd) {
			newEnd = ltStart;
		}
		int dotEnd;
		if ((dotEnd = str.lastIndexOf(".", newEnd)) != -1) {
			newStart = dotEnd + 1;
		}
		str = Character.toLowerCase(str.charAt(newStart)) + str.substring(newStart + 1, newEnd);

		if (str.equals("boolean")) {
			str = "bool";
		}
		return str;
	}

	private class ParamType {
		private String name;
		private final TypeName type;
		private final Modifier[] modifiers;
		final String comment;

		public ParamType(Map.Entry<String, String> nameAndDoc, TypeName type, Set<String> usedNames, int slot) {
			this.name = nameAndDoc != null ? nameAndDoc.getKey() : null;
			if (this.name != null) {
				if (usedNames.contains(this.name)) {
					System.err.println(String.format("Overridden parameter name detected in %s %s %s slot %d, resetting", classNode.name, methodNode.name, methodNode.desc, slot));
					this.name = null;
				} else {
					usedNames.add(this.name);
				}
			}
			this.comment = nameAndDoc == null ? null : nameAndDoc.getValue();
			this.type = type;
			this.modifiers = new ModifierBuilder(0)
					.getModifiers(ModifierBuilder.Type.PARAM);
		}
		
		private void fillName(Set<String> usedNames) {
			if (name != null)
				return;
			name = reserveValidName(suggestName(type), usedNames);
		}
	}

	private void addExceptions() {
		if (signature != null) {
			for (TypeName each : signature.thrown) {
				builder.addException(each);
			}
			return;
		}
		List<String> exceptions = methodNode.exceptions;
		if (exceptions != null) {
			for (String internalName : exceptions) {
				builder.addException(ClassBuilder.parseInternalName(internalName));
			}
		}
	}

	private void addJavaDoc() {
		String javaDoc = mappings.getMethodDoc(new EntryTriple(classNode.name, methodNode.name, methodNode.desc));
		if (javaDoc != null) {
			builder.addJavadoc(javaDoc);
		}
	}

	public MethodSpec build() {
		return builder.build();
	}
}
