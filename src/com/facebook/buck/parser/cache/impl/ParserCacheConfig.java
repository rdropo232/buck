/*
 * Copyright (c) Facebook, Inc. and its affiliates.
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

package com.facebook.buck.parser.cache.impl;

import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.config.ConfigView;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.parser.cache.ParserCacheException;
import java.nio.file.Path;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Class that implements the {@link com.facebook.buck.parser.cache.impl.ParserCache} configuration.
 */
@BuckStyleValue
public abstract class ParserCacheConfig implements ConfigView<BuckConfig> {
  private static final Logger LOG = Logger.get(ParserCacheConfig.class);

  static final String PARSER_CACHE_SECTION_NAME = "parser";
  static final String PARSER_CACHE_LOCAL_LOCATION_NAME = "dir";
  private static final String PARSER_CACHE_LOCAL_MODE_NAME = "dir_mode";
  private static final String DEFAULT_PARSER_CACHE_MODE_VALUE = "NONE";

  private static final String MANIFEST_SERVICE_SECTION_NAME = "manifestservice";
  private static final String MANIFEST_SERVICE_THRIFT_ENDPOINT_NAME = "hybrid_thrift_endpoint";
  private static final String MANIFEST_SERVICE_MODE_NAME = "remote_parser_caching_access_mode";

  @Override
  public abstract BuckConfig getDelegate();

  public static ParserCacheConfig of(BuckConfig delegate) {
    return ImmutableParserCacheConfig.of(delegate);
  }

  private ParserCacheAccessMode getCacheMode(String cacheType) throws ParserCacheException {
    String cacheMode =
        getDelegate()
            .getValue(PARSER_CACHE_SECTION_NAME, cacheType)
            .orElse(DEFAULT_PARSER_CACHE_MODE_VALUE);
    ParserCacheAccessMode result;
    try {
      result = ParserCacheAccessMode.valueOf(cacheMode.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ParserCacheException(
          "Unusable cache.%s: '%s'", PARSER_CACHE_LOCAL_MODE_NAME, cacheMode);
    }
    return result;
  }

  /** Obtains a {@link ParserDirCacheEntry} from the {@link BuckConfig}. */
  @Value.Lazy
  protected ParserDirCacheEntry obtainDirEntry() {
    String dirLocation =
        getDelegate()
            .getValue(PARSER_CACHE_SECTION_NAME, PARSER_CACHE_LOCAL_LOCATION_NAME)
            .orElse(null);

    if (dirLocation == null) {
      return ImmutableParserDirCacheEntry.of(
          Optional.empty(), ParserCacheAccessMode.NONE); // Disable local cache.
    }

    ParserCacheAccessMode parserCacheAccessMode = ParserCacheAccessMode.NONE;
    try {
      parserCacheAccessMode = getCacheMode(PARSER_CACHE_LOCAL_MODE_NAME);
    } catch (ParserCacheException t) {
      LOG.error(t, "Could not get ParserCacheAccessMode for local AbstractCacheConfig.");
    }

    if (parserCacheAccessMode == ParserCacheAccessMode.NONE) {
      return ImmutableParserDirCacheEntry.of(
          Optional.empty(), ParserCacheAccessMode.NONE); // Disable local cache.
    }

    Path pathToCacheDir;
    ProjectFilesystem filesystem = getDelegate().getFilesystem();
    if (dirLocation.isEmpty()) {
      pathToCacheDir = filesystem.getBuckPaths().getBuckOut().resolve(dirLocation);
    } else {
      pathToCacheDir = filesystem.getPath(dirLocation);
      if (!pathToCacheDir.isAbsolute()) {
        pathToCacheDir = filesystem.getBuckPaths().getBuckOut().resolve(pathToCacheDir);
      }
    }
    return ImmutableParserDirCacheEntry.of(Optional.of(pathToCacheDir), parserCacheAccessMode);
  }

  /** Obtains a {@link ParserDirCacheEntry} from the {@link BuckConfig}. */
  @Value.Lazy
  protected ParserRemoteCacheEntry obtainRemoteEntry() {
    String thriftEndpoint =
        getDelegate()
            .getValue(MANIFEST_SERVICE_SECTION_NAME, MANIFEST_SERVICE_THRIFT_ENDPOINT_NAME)
            .orElse(null);

    if (thriftEndpoint == null) {
      // No endpoint specified. Disable remote cache.
      return ImmutableParserRemoteCacheEntry.of(ParserCacheAccessMode.NONE);
    }

    ParserCacheAccessMode parserCacheAccessMode = ParserCacheAccessMode.NONE;
    try {
      parserCacheAccessMode = getCacheMode(MANIFEST_SERVICE_MODE_NAME);
    } catch (ParserCacheException t) {
      LOG.error(t, "Could not get ParserCacheAccessMode for remote AbstractCacheConfig.");
    }

    if (parserCacheAccessMode == ParserCacheAccessMode.NONE) {
      return ImmutableParserRemoteCacheEntry.of(ParserCacheAccessMode.NONE); // Disable local cache.
    }

    return ImmutableParserRemoteCacheEntry.of(parserCacheAccessMode);
  }

  /** @returns the location for the local cache. */
  @Value.Lazy
  public Optional<Path> getDirCacheLocation() {
    ParserDirCacheEntry parserDirCacheEntry = obtainDirEntry();
    if (parserDirCacheEntry != null) {
      return parserDirCacheEntry.getDirCacheLocation();
    }

    return Optional.empty();
  }

  /**
   * @returns {@code true} if the {@link LocalCacheStorage} is enabled, and {@code false} if not.
   */
  @Value.Lazy
  public boolean isDirParserCacheEnabled() {
    ParserDirCacheEntry parserDirCacheEntry = obtainDirEntry();

    return parserDirCacheEntry.getDirCacheLocation().isPresent()
        && parserDirCacheEntry.getDirCacheMode() != ParserCacheAccessMode.NONE;
  }

  /** @returns {@link ParserCacheAccessMode} associated with the {@link LocalCacheStorage} */
  public ParserCacheAccessMode getDirCacheAccessMode() {
    ParserDirCacheEntry parserDirCacheEntry = obtainDirEntry();
    if (parserDirCacheEntry != null) {
      return parserDirCacheEntry.getDirCacheMode();
    }

    return ParserCacheAccessMode.NONE;
  }

  /**
   * @returns {@code true} if the {@link RemoteManifestServiceCacheStorage} is enabled, otherwise
   *     {@code false}.
   */
  public boolean isRemoteParserCacheEnabled() {
    ParserRemoteCacheEntry parserRemoteCacheEntry = obtainRemoteEntry();
    return parserRemoteCacheEntry.getRemoteCacheMode() != ParserCacheAccessMode.NONE;
  }

  /** @returns the access mode for the {@link RemoteManifestServiceCacheStorage}. */
  public ParserCacheAccessMode getRemoteCacheAccessMode() {
    ParserRemoteCacheEntry parserRemoteCacheEntry = obtainRemoteEntry();
    if (parserRemoteCacheEntry != null) {
      return parserRemoteCacheEntry.getRemoteCacheMode();
    }

    return ParserCacheAccessMode.NONE;
  }

  /**
   * @returns {@code true} if there is a cache storage that is enabled. Otherwise, {@code false}.
   */
  public boolean isParserCacheEnabled() {
    return isDirParserCacheEnabled() || isRemoteParserCacheEnabled();
  }
}
