/*
 * Copyright 2012 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.j2objc.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Table;
import com.google.devtools.j2objc.ast.AbstractTypeDeclaration;
import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;

import org.eclipse.jdt.core.dom.IMethodBinding;

/**
 * Tracks classes, fields, and methods that are referenced in source code.
 *
 * @author Daniel Connelly
 */
public class CodeReferenceMap {

  public static class Builder {
    private final Set<String> deadClasses = new HashSet<String>();
    private final Table<String, String, Set<String>> deadMethods = HashBasedTable.create();
    private final ListMultimap<String, String> deadFields = ArrayListMultimap.create();

    public CodeReferenceMap build() {
      ImmutableTable.Builder<String, String, ImmutableSet<String>> deadMethodsBuilder =
          ImmutableTable.builder();
      for (Table.Cell<String, String, Set<String>> cell : this.deadMethods.cellSet()) {
        deadMethodsBuilder.put(
            cell.getRowKey(),
            cell.getColumnKey(),
            ImmutableSet.copyOf(cell.getValue()));
      }
      return new CodeReferenceMap(
          ImmutableSet.copyOf(deadClasses),
          deadMethodsBuilder.build(),
          ImmutableMultimap.copyOf(deadFields));
    }

    public Builder addDeadClass(String clazz) {
      deadClasses.add(clazz);
      return this;
    }

    public Builder addDeadMethod(String clazz, String name, String signature) {
      if (!deadMethods.contains(clazz, name)) {
        deadMethods.put(clazz, name, new HashSet<String>());
      }
      deadMethods.get(clazz, name).add(signature);
      return this;
    }

    public Builder addDeadField(String clazz, String field) {
      deadFields.put(clazz, field);
      return this;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final ImmutableSet<String> deadClasses;
  private final ImmutableTable<String, String, ImmutableSet<String>> deadMethods;
  private final ImmutableMultimap<String, String> deadFields;
  private final Set<String> hasConstructorRemovedClasses = new HashSet<>();

  private CodeReferenceMap(
      ImmutableSet<String> deadClasses,
      ImmutableTable<String, String, ImmutableSet<String>> deadMethods,
      ImmutableMultimap<String, String> deadFields) {
    this.deadClasses = deadClasses;
    this.deadMethods = deadMethods;
    this.deadFields = deadFields;
  }

  public boolean containsClass(String clazz) {
    return deadClasses.contains(clazz);
  }

  public boolean containsClass(AbstractTypeDeclaration node) {
    return containsClass(node.getTypeBinding().getBinaryName());
  }

  public boolean containsMethod(String clazz, String name, String signature) {
    return deadClasses.contains(clazz)
        || (deadMethods.contains(clazz, name) && deadMethods.get(clazz, name).contains(signature));
  }

  public boolean containsMethod(IMethodBinding binding) {
    String className = binding.getDeclaringClass().getBinaryName();
    String methodName = binding.getName();
    String methodSig = BindingUtil.getSignature(binding);
    return containsMethod(className, methodName, methodSig);
  }

  //TODO(user): Revisit this method and remove typeUtil
  //  Problem: need access to a typeUtil for ProguardNameUtil.getSignature method.
  public boolean containsMethod(ExecutableElement method, TypeUtil typeUtil) {
    String className = ElementUtil.getName(ElementUtil.getDeclaringClass(method));
    String methodName = ProguardNameUtil.getProGuardName(method);
    String methodSig = ProguardNameUtil.getProGuardSignature(method, typeUtil);
    return containsMethod(className, methodName, methodSig);
  }

  public boolean containsField(String clazz, String field) {
    return deadClasses.contains(clazz) || deadFields.containsEntry(clazz, field);
  }

  public boolean isEmpty() {
    return deadClasses.isEmpty() && deadMethods.isEmpty() && deadFields.isEmpty();
  }

  public void addConstructorRemovedClass(String clazz) {
    hasConstructorRemovedClasses.add(clazz);
  }

  public boolean classHasConstructorRemoved(String clazz) {
    return hasConstructorRemovedClasses.contains(clazz);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();

    builder.append(deadClasses.asList().toString() + "\n");
    builder.append(deadFields.toString() + "\n");
    builder.append(deadMethods.toString());

    return builder.toString();
  }
}
