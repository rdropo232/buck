/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cxx;

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.RuleKeyBuilder;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.base.Optional;

import org.immutables.value.Value;

/**
 * Wraps a header directory to add to the preprocessors search path.
 */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractCxxHeadersDir extends CxxHeaders {

  @Override
  @Value.Parameter
  public abstract CxxPreprocessables.IncludeType getIncludeType();

  @Override
  @Value.Parameter
  public abstract SourcePath getRoot();

  @Override
  public SourcePath getIncludeRoot() {
    return getRoot();
  }

  @Override
  public Optional<SourcePath> getHeaderMap() {
    return Optional.absent();
  }

  @Override
  public void addToHeaderPathNormalizer(HeaderPathNormalizer.Builder builder) {
    builder.addHeaderDir(getRoot());
  }

  @Override
  public Iterable<BuildRule> getDeps(SourcePathResolver resolver) {
    return resolver.filterBuildRuleInputs(getRoot());
  }

  @Override
  public RuleKeyBuilder appendToRuleKey(RuleKeyBuilder builder) {
    builder.setReflectively("type", getIncludeType());
    builder.setReflectively("root", getRoot());
    return builder;
  }

}
