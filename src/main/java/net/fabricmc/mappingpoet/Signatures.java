package net.fabricmc.mappingpoet;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

public final class Signatures {

	public static Map.Entry<Integer, TypeName> parseParameterizedType(String signature, int startOffset) {
		Deque<Frame<?>> frames = new LinkedList<>();
		Deque<LinkedList<TypeParameterEntry<?>>> typeParametersStack = new LinkedList<>();
		typeParametersStack.addLast(new LinkedList<>());

		int index = startOffset;
		// the loop parses a type and try to quit levels if possible
		do {
			char ch = signature.charAt(index);
			boolean parseExactType = true;
			boolean bounded = false;
			boolean extendsBound = false;

			switch (ch) {
			case '*': {
				index++;
				parseExactType = false;
				typeParametersStack.getLast().add(TypeParameterEntry.wildcard());
				break;
			}
			case '+': {
				index++;
				bounded = true;
				extendsBound = true;
				break;
			}
			case '-': {
				index++;
				bounded = true;
				extendsBound = false;
				break;
			}
			default: {
				// do nothing
			}
			}

			if (parseExactType) {
				int arrayLevel = 0;
				while ((ch = signature.charAt(index)) == '[') {
					index++;
					arrayLevel++;
				}

				index++; // whatever the prefix is it's consumed
				switch (ch) {
				case 'B':
				case 'C':
				case 'D':
				case 'F':
				case 'I':
				case 'J':
				case 'S':
				case 'Z': {
					// primitives
					typeParametersStack.getLast().add(new TypeParameterEntry<>(getPrimitive(ch), arrayLevel, bounded, extendsBound));
					break;
				}
				case 'T': {
					// "TE;" for <E>
					int nameStart = index;
					while (signature.charAt(index) != ';') {
						index++;
					}
					String typeVarName = signature.substring(nameStart, index);
					typeParametersStack.getLast().add(new TypeParameterEntry<>(TypeVariableName.get(typeVarName), arrayLevel, bounded, extendsBound));
					index++; // read ending ";"
					break;
				}
				case 'L': {
					// Lcom/example/Outer<TA;TB;>.Inner<TC;>;
					// Lcom/example/Outer$Inner<TA;>;
					// dot only appears after ">"!
					int nameStart = index;
					ClassName currentClass = null;
					int nextSimpleNamePrev = -1;
					do {
						ch = signature.charAt(index);

						if (ch == '/') {
							if (currentClass != null) {
								throw errorAt(signature, index);
							}
							nextSimpleNamePrev = index;
						}

						if (ch == '$' || ch == '<' || ch == ';') {
							if (currentClass == null) {
								String packageName = nextSimpleNamePrev == -1 ? "" : signature.substring(nameStart, nextSimpleNamePrev).replace('/', '.');
								String simpleName = signature.substring(nextSimpleNamePrev + 1, index);
								currentClass = ClassName.get(packageName, simpleName);
							} else {
								String simpleName = signature.substring(nextSimpleNamePrev + 1, index);
								currentClass = currentClass.nestedClass(simpleName);
							}
							nextSimpleNamePrev = index;
						}

						index++;
					} while (ch != '<' && ch != ';');

					assert currentClass != null;
					if (ch == ';') {
						typeParametersStack.getLast().add(new TypeParameterEntry<>(currentClass, arrayLevel, bounded, extendsBound));
					}

					if (ch == '<') {
						typeParametersStack.addLast(new LinkedList<>());
						frames.addLast(Frame.ofClass(new TypeParameterEntry<>(currentClass, arrayLevel, bounded, extendsBound)));
					}
					break;
				}
				default: {
					throw errorAt(signature, index);
				}
				}
			}

			// quit generics
			quitLoop:
			while (!frames.isEmpty() && signature.charAt(index) == '>') {
				// pop
				TypeParameterEntry<?> genericElement = frames.removeLast().acceptParameters(typeParametersStack.removeLast());
				typeParametersStack.getLast().add(genericElement);
				index++;

				// followups like .B<E> in A<T>.B<E>
				if ((ch = signature.charAt(index)) != ';') {
					if (ch != '.') {
						throw errorAt(signature, index);
					}
					index++;
					int innerNameStart = index;
					TypeParameterEntry<?> modTarget = typeParametersStack.getLast().removeLast();
					if (!(modTarget.typeName instanceof ParameterizedTypeName)) {
						throw errorAt(signature, index);
					}
					@SuppressWarnings("unchecked")
					TypeParameterEntry<ParameterizedTypeName> enclosing = (TypeParameterEntry<ParameterizedTypeName>) modTarget;

					while (true) {
						ch = signature.charAt(index);
						if (ch == '.' || ch == ';' || ch == '<') {
							String simpleName = signature.substring(innerNameStart, index);
							if (ch == '.' || ch == ';') {
								enclosing = enclosing.map(name -> name.nestedClass(simpleName));
							} else {
								frames.addLast(Frame.ofGenericInnerClass(enclosing, simpleName));
								typeParametersStack.addLast(new LinkedList<>());
								index++;
								break quitLoop;
							}
						}

						index++;
					}
				} else {
					index++;
				}

			}

			assert frames.size() == typeParametersStack.size() - 1;
		} while (frames.size() > 0);

		assert typeParametersStack.size() == 1;
		assert typeParametersStack.getLast().size() == 1;
		return new AbstractMap.SimpleImmutableEntry<>(index, typeParametersStack.getLast().get(0).resolve());
	}

	private static IllegalArgumentException errorAt(String signature, int index) {
		return new IllegalArgumentException(String.format("Signature format error at %d for \"%s\"", index, signature));
	}

	public static TypeName wrap(TypeName component, int level, boolean bounded, boolean extendsBound) {
		TypeName ret = component;
		for (int i = 0; i < level; i++) {
			ret = ArrayTypeName.of(ret);
		}
		return bounded ? extendsBound ? WildcardTypeName.subtypeOf(ret) : WildcardTypeName.supertypeOf(ret) : ret;
	}

	public static TypeName getPrimitive(char c) {
		switch (c) {
		case 'B':
			return TypeName.BYTE;
		case 'C':
			return TypeName.CHAR;
		case 'D':
			return TypeName.DOUBLE;
		case 'F':
			return TypeName.FLOAT;
		case 'I':
			return TypeName.INT;
		case 'J':
			return TypeName.LONG;
		case 'S':
			return TypeName.SHORT;
		case 'V':
			return TypeName.VOID;
		case 'Z':
			return TypeName.BOOLEAN;
		}
		throw new IllegalArgumentException("Invalid primitive " + c);
	}

	@FunctionalInterface
	interface Frame<T extends TypeName> {
		static Frame<ParameterizedTypeName> ofClass(TypeParameterEntry<ClassName> classNameEntry) {
			return parameters -> classNameEntry.map(name -> ParameterizedTypeName.get(name, parameters));
		}

		static Frame<ParameterizedTypeName> ofGenericInnerClass(TypeParameterEntry<ParameterizedTypeName> outerClassEntry, String innerName) {
			return parameters -> outerClassEntry.map(name -> name.nestedClass(innerName, Arrays.asList(parameters)));
		}
		
		default TypeParameterEntry<T> acceptParameters(List<TypeParameterEntry<?>> superParameters) {
			TypeName[] typeNames = new TypeName[superParameters.size()];
			int len = typeNames.length;
			Iterator<TypeParameterEntry<?>> itr = superParameters.iterator();
			for (int i = 0; i < len; i++) {
				typeNames[i] = itr.next().resolve();
			}
			return acceptParameters(typeNames);
		}

		TypeParameterEntry<T> acceptParameters(TypeName[] parameters);
	}

	// exists so that [LA<TB;>.C<TD;>; get parsed as A<B>.C<D>[] than A<B>[].C<D>
	static final class TypeParameterEntry<T extends TypeName> {
		final T typeName;
		final int arrayLevel;
		final boolean bounded;
		final boolean extendsBound;
		
		static TypeParameterEntry<WildcardTypeName> wildcard() {
			return new TypeParameterEntry<>(WildcardTypeName.subtypeOf(ClassName.OBJECT), 0, false, false);
		}
		
		TypeParameterEntry(T typeName, int arrayLevel, boolean bounded, boolean extendsBound) {
			this.typeName = typeName;
			this.arrayLevel = arrayLevel;
			this.bounded = bounded;
			this.extendsBound = extendsBound;
		}
		
		<U extends TypeName> TypeParameterEntry<U> map(Function<? super T, ? extends U> mapper) {
			return new TypeParameterEntry<>(mapper.apply(typeName), arrayLevel, bounded, extendsBound);
		}
		
		TypeName resolve() {
			return Signatures.wrap(typeName, arrayLevel, bounded, extendsBound);
		}
	}
}
