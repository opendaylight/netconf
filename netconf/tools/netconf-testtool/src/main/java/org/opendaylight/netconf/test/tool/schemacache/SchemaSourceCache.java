/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.schemacache;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendaylight.netconf.test.tool.TestToolUtils;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource.Costs;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.model.repo.util.AbstractSchemaSourceCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache implementation that stores schemas in form of files under provided folder.
 */
public final class SchemaSourceCache<T extends SchemaSourceRepresentation>
        extends AbstractSchemaSourceCache<T> {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaSourceCache.class);

    public static final Pattern CACHED_FILE_PATTERN = Pattern.compile(
                    ".*/(?<moduleName>[^@]+)" + "(@(?<revision>" + SourceIdentifier.REVISION_PATTERN + "))?.yang");

    private final Class<T> representation;
    private final Set<String> modelList;
    private Map<String, ModelData> cachedSchemas;

    public SchemaSourceCache(
            final SchemaSourceRegistry consumer, final Class<T> representation, final Set<String> modelList) {
        super(consumer, representation, Costs.LOCAL_IO);
        this.representation = representation;
        this.modelList = Preconditions.checkNotNull(modelList);
        init();
    }

    /**
     * Restore cache state.
     */
    private void init() {
        cachedSchemas = new HashMap<>();
        for (String modelPath: modelList) {
            Optional<SourceIdentifier> sourceIdentifierOptional = getSourceIdentifier(modelPath);
            if (sourceIdentifierOptional.isPresent()) {
                SourceIdentifier sourceIdentifier = sourceIdentifierOptional.get();
                cachedSchemas.put(sourceIdentifier.toYangFilename(), new ModelData(sourceIdentifier, modelPath));
            } else {
                LOG.debug("Skipping caching model {}, cannot restore source identifier from model path,"
                        + " does not match {}", modelPath, CACHED_FILE_PATTERN);
            }
        }
        for (final ModelData cachedSchema : cachedSchemas.values()) {
            register(cachedSchema.getId());
        }
    }

    @Override
    public synchronized ListenableFuture<? extends T> getSource(final SourceIdentifier sourceIdentifier) {
        ModelData modelData = cachedSchemas.get(sourceIdentifier.toYangFilename());
        if (modelData != null) {
            final SchemaSourceRepresentation restored = restoreAsType(modelData.getId(), modelData.getPath());
            return Futures.immediateFuture(representation.cast(restored));
        }

        LOG.debug("Source {} not found in cache as {}", sourceIdentifier);
        return Futures.immediateFailedFuture(new MissingSchemaSourceException("Source not found", sourceIdentifier));
    }

    @Override
    protected synchronized void offer(final T source) {
        LOG.trace("Source {} offered to cache", source.getIdentifier());
    }

    private static YangTextSchemaSource restoreAsType(final SourceIdentifier sourceIdentifier,
            final String cachedSource) {
        return new YangTextSchemaSource(sourceIdentifier) {

            @Override
            protected ToStringHelper addToStringAttributes(final ToStringHelper toStringHelper) {
                return toStringHelper;
            }

            @Override
            public InputStream openStream() throws IOException {
                return TestToolUtils.getDataAsStream(cachedSource);
            }
        };
    }

    private static Optional<SourceIdentifier> getSourceIdentifier(final String fileName) {
        final Matcher matcher = CACHED_FILE_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            final String moduleName = matcher.group("moduleName");
            final String revision = matcher.group("revision");
            return Optional.of(RevisionSourceIdentifier.create(moduleName, Optional.ofNullable(revision)));
        }
        return Optional.empty();
    }
}
