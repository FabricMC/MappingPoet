package net.fabricmc.mappingpoet;

import java.net.URLClassLoader;
import java.util.Comparator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import com.squareup.javapoet.TypeName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SignaturesTest {

	@Test
	public void testRandomMapType() {
		//signature Ljava/util/Map<Ljava/util/Map$Entry<[[Ljava/lang/String;[Ljava/util/List<[I>;>;[[[D>;
		//Map<Map.Entry<String[][], List<int[]>[]>, double[][][]> map = new HashMap<>();
		String signature = "Ljava/util/Map<Ljava/util/Map$Entry<[[Ljava/lang/String;[Ljava/util/List<[I>;>;[[[D>;";
		Map.Entry<Integer, TypeName> result = Signatures.parseParameterizedType(signature, 0);
		System.out.println(result.getKey() + " " + result.getValue());

		Assertions.assertEquals(85, result.getKey().intValue());
		Assertions.assertEquals("java.util.Map<java.util.Map.Entry<java.lang.String[][], java.util.List<int[]>[]>, double[][][]>", result.getValue().toString());
	}

	@Test
	public void testCrazyOne() {
		String classSignature = "<B::Ljava/util/Comparator<-TA;>;C:Ljava/lang/ClassLoader;:Ljava/lang/Iterable<*>;>Ljava/lang/Object;";
		Map.Entry<Integer, TypeName> result = Signatures.parseParameterizedType(classSignature, 4);
		System.out.println(result.getKey() + " " + result.getValue()); // key = 32
		Assertions.assertEquals(32, result.getKey().intValue());
		Assertions.assertEquals("java.util.Comparator<? super A>", result.getValue().toString());

		result = Signatures.parseParameterizedType(classSignature, 34);
		System.out.println(result.getKey() + " " + result.getValue()); // key = 57
		Assertions.assertEquals(57, result.getKey().intValue());
		Assertions.assertEquals("java.lang.ClassLoader", result.getValue().toString());
		
		result = Signatures.parseParameterizedType(classSignature, 58);
		System.out.println(result.getKey() + " " + result.getValue()); // key = 81
		Assertions.assertEquals(81, result.getKey().intValue());
		Assertions.assertEquals("java.lang.Iterable<?>", result.getValue().toString());
	}

	@Test
	public void soo() {
		TestOuter<Integer>.Inner<Comparator<Integer>, URLClassLoader>.ExtraInner<UnaryOperator<Map<int[][], BiFunction<Comparator<Integer>, Integer, URLClassLoader>>>> local = new TestOuter<Integer>().new Inner<Comparator<Integer>, URLClassLoader>().new ExtraInner<UnaryOperator<Map<int[][], BiFunction<Comparator<Integer>, Integer, URLClassLoader>>>>();
		local.hashCode();

		// signature Lnet/fabricmc/mappingpoet/TestOuter<Ljava/lang/Integer;>.Inner<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/net/URLClassLoader;>.ExtraInner<Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/lang/Integer;Ljava/net/URLClassLoader;>;>;>;>;
		String signature = "Lnet/fabricmc/mappingpoet/TestOuter<Ljava/lang/Integer;>.Inner<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/net/URLClassLoader;>.ExtraInner<Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/lang/Integer;Ljava/net/URLClassLoader;>;>;>;>;";
		Map.Entry<Integer, TypeName> result = Signatures.parseParameterizedType(signature, 0);
		System.out.println(result.getKey() + " " + result.getValue()); // key = 322

		Assertions.assertEquals(322, result.getKey().intValue());
		Assertions.assertEquals("net.fabricmc.mappingpoet.TestOuter<java.lang.Integer>.Inner<java.util.Comparator<java.lang.Integer>, java.net.URLClassLoader>.ExtraInner<java.util.function.UnaryOperator<java.util.Map<int[][], java.util.function.BiFunction<java.util.Comparator<java.lang.Integer>, java.lang.Integer, java.net.URLClassLoader>>>>", result.getValue().toString());
	}

	@Test
	public void arrSoo() {
		@SuppressWarnings("unchecked")
		TestOuter<Integer>.Inner<Comparator<Integer>, URLClassLoader>.ExtraInner<UnaryOperator<Map<int[][], BiFunction<Comparator<Integer>, Integer, URLClassLoader>>>>[][] arr = (TestOuter<Integer>.Inner<Comparator<Integer>, URLClassLoader>.ExtraInner<UnaryOperator<Map<int[][], BiFunction<Comparator<Integer>, Integer, URLClassLoader>>>>[][]) new TestOuter<?>.Inner<?, ?>.ExtraInner<?>[0][];
		arr.toString();
		// signature [[Lnet/fabricmc/mappingpoet/TestOuter<Ljava/lang/Integer;>.Inner<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/net/URLClassLoader;>.ExtraInner<Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/lang/Integer;Ljava/net/URLClassLoader;>;>;>;>;
		String arraySignature = "[[Lnet/fabricmc/mappingpoet/TestOuter<Ljava/lang/Integer;>.Inner<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/net/URLClassLoader;>.ExtraInner<Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<Ljava/util/Comparator<Ljava/lang/Integer;>;Ljava/lang/Integer;Ljava/net/URLClassLoader;>;>;>;>;";
		Map.Entry<Integer, TypeName> result = Signatures.parseParameterizedType(arraySignature, 0);
		System.out.println(result.getKey() + " " + result.getValue()); // key = 324

		Assertions.assertEquals(324, result.getKey().intValue());
		Assertions.assertEquals("net.fabricmc.mappingpoet.TestOuter<java.lang.Integer>.Inner<java.util.Comparator<java.lang.Integer>, java.net.URLClassLoader>.ExtraInner<java.util.function.UnaryOperator<java.util.Map<int[][], java.util.function.BiFunction<java.util.Comparator<java.lang.Integer>, java.lang.Integer, java.net.URLClassLoader>>>>[][]", result.getValue().toString());
	}
}
