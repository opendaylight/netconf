/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.Strings;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Service;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice.SchemaResourcesDTO;
import org.opendaylight.netconf.sal.connect.netconf.NetconfStateSchemasResolverImpl;
import org.opendaylight.netconf.topology.api.SchemaResourceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactoryConfiguration;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.model.repo.util.InMemorySchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.ASTSchemaSource;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToASTTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple single-node implementation of the {@link SchemaResourceManager} contract. Operates on the specified base
 * root directory, where a number of independent subdirectories are created, each for a global default and anything
 * encountered based on configuration.
 */
@Beta
@Service
@Singleton
public final class DefaultSchemaResourceManager implements SchemaResourceManager {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSchemaResourceManager.class);

    @GuardedBy("this")
    private final Map<String, SchemaResourcesDTO> resources = new HashMap<>();
    private final @NonNull SchemaResourcesDTO defaultResources;
    private final String defaultSubdirectory;
    private final String rootDirectory;

    @Inject
    public DefaultSchemaResourceManager() {
        this("cache", "schema");
    }

    public DefaultSchemaResourceManager(final String rootDirectory, final String defaultSubdirectory) {
        this.rootDirectory = requireNonNull(rootDirectory);
        this.defaultSubdirectory = requireNonNull(defaultSubdirectory);
        this.defaultResources = createResources(defaultSubdirectory);
    }

    @Override
    public SchemaResourcesDTO getSchemaResources(final NetconfNode node, final Object nodeId) {
        final String subdir = node.getSchemaCacheDirectory();
        if (defaultSubdirectory.equals(subdir)) {
            // Fast path for default devices
            return defaultResources;
        }
        if (Strings.isNullOrEmpty(subdir)) {
            // FIXME: we probably want to change semantics here:
            //        - update model to not allow empty name
            //        - silently default to the default
            LOG.warn("schema-cache-directory for {} is null or empty;  using the default {}", nodeId,
                defaultSubdirectory);
            return defaultResources;
        }

        LOG.info("Netconf connector for device {} will use schema cache directory {} instead of {}", nodeId, subdir,
            defaultSubdirectory);
        return getSchemaResources(subdir);
    }

    private synchronized @NonNull SchemaResourcesDTO getSchemaResources(final String subdir) {
        // Fast path for unusual devices
        final SchemaResourcesDTO existing = resources.get(subdir);
        if (existing != null) {
            return existing;
        }

        final SchemaResourcesDTO created = createResources(subdir);
        resources.put(subdir, created);
        return created;
    }

    private @NonNull SchemaResourcesDTO createResources(final String subdir) {
        // Setup the baseline empty registry
        // FIXME: add YangParserFactory argument
        final SharedSchemaRepository repository = new SharedSchemaRepository(subdir);

        // Teach the registry how to transform YANG text to ASTSchemaSource internally
        repository.registerSchemaSourceListener(TextToASTTransformer.create(repository, repository));

        // Attach a soft cache of ASTSchemaSource instances. This is important during convergence when we are fishing
        // for a consistent set of modules, as it skips the need to re-parse the text sources multiple times. It also
        // helps establishing different sets of contexts, as they can share this pre-made cache.
        repository.registerSchemaSourceListener(
            // FIXME: add knobs to control cache lifetime explicitly
            InMemorySchemaSourceCache.createSoftCache(repository, ASTSchemaSource.class));

        // Attach the filesystem cache, providing persistence capability, so that restarts do not require us to
        // re-populate the cache. This also acts as a side-load capability, as anything pre-populated into that
        // directory will not be fetched from the device.
        repository.registerSchemaSourceListener(new FilesystemSchemaSourceCache<>(repository,
                YangTextSchemaSource.class, new File(rootDirectory + File.separator + subdir)));

        return new SchemaResourcesDTO(repository, repository,
            repository.createEffectiveModelContextFactory(SchemaContextFactoryConfiguration.getDefault()),
            new NetconfStateSchemasResolverImpl());
    }
}
