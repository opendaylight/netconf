/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.Strings;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.fs.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.model.repo.spi.SoftSchemaSourceCache;
import org.opendaylight.yangtools.yang.model.spi.source.YangIRSource;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToIRTransformer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.RequireServiceComponentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple single-node implementation of the {@link SchemaResourceManager} contract. Operates on the specified base
 * root directory, where a number of independent subdirectories are created, each for a global default and anything
 * encountered based on configuration.
 */
@Beta
@Singleton
@Component(immediate = true)
@RequireServiceComponentRuntime
public final class DefaultSchemaResourceManager implements SchemaResourceManager {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSchemaResourceManager.class);

    @GuardedBy("this")
    private final Map<String, DeviceNetconfSchemaProvider> resources = new HashMap<>();
    private final @NonNull DeviceNetconfSchemaProvider defaultResources;
    private final YangParserFactory parserFactory;
    private final String defaultSubdirectory;
    private final String rootDirectory;

    @Activate
    @Inject
    public DefaultSchemaResourceManager(@Reference final YangParserFactory parserFactory) {
        this(parserFactory, "cache", "schema");
    }

    public DefaultSchemaResourceManager(final YangParserFactory parserFactory, final String rootDirectory,
            final String defaultSubdirectory) {
        this.parserFactory = requireNonNull(parserFactory);
        this.rootDirectory = requireNonNull(rootDirectory);
        this.defaultSubdirectory = requireNonNull(defaultSubdirectory);
        defaultResources = createResources(defaultSubdirectory);
        LOG.info("Schema Resource Manager instantiated on {}/{}", rootDirectory, defaultSubdirectory);
    }

    @Override
    public DeviceNetconfSchemaProvider getSchemaResources(final String subdir, final Object nodeId) {
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

    private synchronized @NonNull DeviceNetconfSchemaProvider getSchemaResources(final String subdir) {
        // Fast path for unusual devices
        final var existing = resources.get(subdir);
        if (existing != null) {
            return existing;
        }

        final var created = createResources(subdir);
        resources.put(subdir, created);
        return created;
    }

    private @NonNull DeviceNetconfSchemaProvider createResources(final String subdir) {
        // Setup the baseline empty registry
        final var repository = new SharedSchemaRepository(subdir, parserFactory);

        // Teach the registry how to transform YANG text to IRSchemaSource internally
        repository.registerSchemaSourceListener(TextToIRTransformer.create(repository, repository));

        // Attach a soft cache of IRSchemaSource instances. This is important during convergence when we are fishing
        // for a consistent set of modules, as it skips the need to re-parse the text sources multiple times. It also
        // helps establishing different sets of contexts, as they can share this pre-made cache.
        repository.registerSchemaSourceListener(new SoftSchemaSourceCache<>(repository, YangIRSource.class));

        // Attach the filesystem cache, providing persistence capability, so that restarts do not require us to
        // re-populate the cache. This also acts as a side-load capability, as anything pre-populated into that
        // directory will not be fetched from the device.
        repository.registerSchemaSourceListener(new FilesystemSchemaSourceCache<>(repository, YangTextSource.class,
            Path.of(rootDirectory).resolve(subdir).toFile()));

        return new DefaultDeviceNetconfSchemaProvider(repository);
    }
}
