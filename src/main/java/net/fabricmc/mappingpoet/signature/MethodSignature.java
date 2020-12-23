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

package net.fabricmc.mappingpoet.signature;

import java.util.List;

import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;

public final class MethodSignature {
	public final List<TypeVariableName> generics;
	public final List<TypeName> parameters;
	public final TypeName result;
	public final List<TypeName> thrown;

	public MethodSignature(List<TypeVariableName> generics, List<TypeName> parameters, TypeName result, List<TypeName> thrown) {
		this.generics = generics;
		this.parameters = parameters;
		this.result = result;
		this.thrown = thrown;
	}
}
