/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.schemacache;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.AbstractSchemaSourceCache;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource.Costs;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache implementation that stores schemas in form of files under provided folder.
 */
public final class SchemaSourceCache<T extends SchemaSourceRepresentation> extends AbstractSchemaSourceCache<T> {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaSourceCache.class);

    private final Class<T> representation;
    private Map<SourceIdentifier, YangModuleInfo> cachedSchemas;

    public SchemaSourceCache(final SchemaSourceRegistry consumer,
                             final Class<T> representation,
                             final Set<YangModuleInfo> moduleList) {
        super(consumer, representation, Costs.LOCAL_IO);
        this.representation = representation;
        initializeCachedSchemas(moduleList);
    }

    /**
     * Restore cache state using input set of modules. Cached schemas are filled with dependencies of input modules too.
     *
     * @param moduleList Set of modules information.
     */
    private void initializeCachedSchemas(final Set<YangModuleInfo> moduleList) {
        // searching for all dependencies
        final Set<YangModuleInfo> allModulesInfo = new HashSet<>(moduleList);
        allModulesInfo.addAll(moduleList.stream()
                .flatMap(yangModuleInfo -> collectYangModuleInfoDependencies(yangModuleInfo, moduleList).stream())
                .collect(Collectors.toSet()));

        // creation of source identifiers for all yang module info
        cachedSchemas = allModulesInfo.stream()
                .map(yangModuleInfo -> {
                    final QName name = yangModuleInfo.getName();
                    final SourceIdentifier revisionSourceIdentifier = new SourceIdentifier(
                            name.getLocalName(), name.getRevision().map(Revision::toString).orElse(null));
                    return new AbstractMap.SimpleEntry<>(revisionSourceIdentifier, yangModuleInfo);
                })
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        cachedSchemas.keySet().forEach(this::register);
    }

    /**
     * Collection of all direct and indirect dependencies of input YANG module info.
     *
     * @param yangModuleInfo       Input YANG module info for which this method collects dependencies.
     * @param collectedModulesInfo Already collected module information.
     * @return Already collected module information union found direct and indirect dependencies.
     */
    private static Set<YangModuleInfo> collectYangModuleInfoDependencies(
            final YangModuleInfo yangModuleInfo, final Set<YangModuleInfo> collectedModulesInfo) {
        // resolution of direct dependencies that haven't already been collected
        final Set<YangModuleInfo> allDependencies = new HashSet<>(collectedModulesInfo);
        final Set<YangModuleInfo> directDependencies = yangModuleInfo.getImportedModules().stream()
                .filter(importedYangModuleInfo -> !collectedModulesInfo.contains(importedYangModuleInfo))
                .collect(Collectors.toSet());
        allDependencies.addAll(directDependencies);

        // resolution of indirect dependencies through recursion
        final Set<YangModuleInfo> indirectDependencies = directDependencies.stream()
                .flatMap(importedYangModuleInfo ->
                        collectYangModuleInfoDependencies(importedYangModuleInfo, allDependencies).stream())
                .collect(Collectors.toSet());
        allDependencies.addAll(indirectDependencies);
        return allDependencies;
    }

    @Override
    public synchronized ListenableFuture<? extends T> getSource(final SourceIdentifier sourceIdentifier) {
        final YangModuleInfo yangModuleInfo = cachedSchemas.get(sourceIdentifier);
        if (yangModuleInfo != null) {
            final YangTextSchemaSource yangTextSchemaSource = YangTextSchemaSource.delegateForCharSource(
                    sourceIdentifier, yangModuleInfo.getYangTextCharSource());
            return Futures.immediateFuture(representation.cast(yangTextSchemaSource));
        }

        LOG.debug("Source {} not found in cache", sourceIdentifier);
        return Futures.immediateFailedFuture(new MissingSchemaSourceException("Source not found", sourceIdentifier));
    }

    @Override
    protected synchronized void offer(final T source) {
        LOG.trace("Source {} offered to cache", source.getIdentifier());
    }
}
