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
import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
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
public final class SchemaSourceCache<T extends SchemaSourceRepresentation> extends AbstractSchemaSourceCache<T> {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaSourceCache.class);

    private final Class<T> representation;
    private final Set<YangModuleInfo> moduleList;
    private Map<String, ModelData> cachedSchemas;

    public SchemaSourceCache(final SchemaSourceRegistry consumer,
                             final Class<T> representation,
                             final Set<YangModuleInfo> moduleList) {
        super(consumer, representation, Costs.LOCAL_IO);
        this.representation = representation;
        this.moduleList = Preconditions.checkNotNull(moduleList);
        initializeCachedSchemas();
    }

    /**
     * Restore cache state.
     */
    private void initializeCachedSchemas() {
        cachedSchemas = moduleList.stream()
                .map(yangModuleInfo -> {
                    final RevisionSourceIdentifier revisionSourceIdentifier = RevisionSourceIdentifier.create(
                            yangModuleInfo.getName().getLocalName(),
                            yangModuleInfo.getName().getRevision());
                    return new AbstractMap.SimpleEntry<>(
                            revisionSourceIdentifier.toYangFilename(),
                            new ModelData(revisionSourceIdentifier, yangModuleInfo));
                })
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        cachedSchemas.values().forEach(modelData -> register(modelData.getSourceIdentifier()));
    }

    @Override
    public synchronized ListenableFuture<? extends T> getSource(final SourceIdentifier sourceIdentifier) {
        final String fileName = sourceIdentifier.toYangFilename();
        ModelData modelData = cachedSchemas.get(fileName);
        if (modelData != null) {
            final SchemaSourceRepresentation restored = restoreAsType(
                    modelData.getSourceIdentifier(), modelData.getYangModuleInfo());
            return Futures.immediateFuture(representation.cast(restored));
        }

        LOG.debug("Source {} not found in cache as {}", sourceIdentifier, fileName);
        return Futures.immediateFailedFuture(new MissingSchemaSourceException("Source not found", sourceIdentifier));
    }

    @Override
    protected synchronized void offer(final T source) {
        LOG.trace("Source {} offered to cache", source.getIdentifier());
    }

    private static YangTextSchemaSource restoreAsType(final SourceIdentifier sourceIdentifier,
                                                      final YangModuleInfo yangModuleInfo) {
        return new YangTextSchemaSource(sourceIdentifier) {

            @Override
            protected ToStringHelper addToStringAttributes(final ToStringHelper toStringHelper) {
                return toStringHelper;
            }

            @Override
            public InputStream openStream() {
                try {
                    return yangModuleInfo.openYangTextStream();
                } catch (final IOException exception) {
                    LOG.error("Cannot open stream to yang module {}.", yangModuleInfo, exception);
                    throw new IllegalStateException(String.format(
                            "YANG text schema source cannot be created from identifier %s and YANG module info %s.",
                            sourceIdentifier, yangModuleInfo), exception);
                }
            }
        };
    }
}
