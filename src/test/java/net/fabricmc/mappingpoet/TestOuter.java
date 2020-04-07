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
