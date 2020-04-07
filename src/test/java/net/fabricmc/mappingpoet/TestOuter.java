package net.fabricmc.mappingpoet;

import java.util.Comparator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

// signature <A::Ljava/lang/Comparable<-TA;>;>Ljava/lang/Object;
public class TestOuter<A extends Comparable<? super A>> {

	// signature <B::Ljava/util/Comparator<-TA;>;C:Ljava/lang/ClassLoader;:Ljava/lang/Iterable<*>;>Ljava/lang/Object;
	class Inner<B extends Comparator<? super A>, C extends ClassLoader & AutoCloseable> {
		// signature <D::Ljava/util/function/UnaryOperator<Ljava/util/Map<[[ILjava/util/function/BiFunction<TB;TA;TC;>;>;>;>Ljava/lang/Object;
		class ExtraInner<D extends UnaryOperator<Map<int[][], BiFunction<B, A, C>>>> {
			
		}
	}
}
