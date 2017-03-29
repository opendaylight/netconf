/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.schema;

import com.google.common.base.Preconditions;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceListener;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.util.TextToASTTransformer;

/**
 * DirectorySchemaContextCache allows to create {@link SchemaContext} from yang sources stored in specified directory.
 */
public class DirectorySchemaContextCache {

    private final SharedSchemaRepository repo;
    private final FilesystemSchemaSourceCache<YangTextSchemaSource> cache;
    private final SchemaContextFactory schemaContextFactory;
    private final Set<SourceIdentifier> registeredSources = new HashSet<>();

    public DirectorySchemaContextCache(final String schemaCachePath) {
        final File schemaCacheDir = new File(schemaCachePath);
        if (!schemaCacheDir.exists()) {
            schemaCacheDir.mkdirs();
        }
        Preconditions.checkArgument(schemaCacheDir.exists());
        Preconditions.checkArgument(schemaCacheDir.isDirectory());
        repo = new SharedSchemaRepository("repo");
        repo.registerSchemaSourceListener(TextToASTTransformer.create(repo, repo));
        cache = new FilesystemSchemaSourceCache<>(repo, YangTextSchemaSource.class, schemaCacheDir);
        repo.registerSchemaSourceListener(new SchemaSourceListener() {
            @Override
            public void schemaSourceEncountered(final SchemaSourceRepresentation source) {

            }

            @Override
            public void schemaSourceRegistered(final Iterable<PotentialSchemaSource<?>> sources) {
                for (final PotentialSchemaSource<?> source : sources) {
                    registeredSources.add(source.getSourceIdentifier());
                }
            }

            @Override
            public void schemaSourceUnregistered(final PotentialSchemaSource<?> source) {
                registeredSources.remove(source.getSourceIdentifier());
            }
        });
        repo.registerSchemaSourceListener(cache);
        schemaContextFactory = repo.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);
    }

    public synchronized SchemaContext createSchemaContext(final Collection<SourceIdentifier> requiredSources) throws SchemaResolutionException {
        for (final SourceIdentifier requiredSource : requiredSources) {
            if (!registeredSources.contains(requiredSource)) {
                repo.registerSchemaSource(cache, PotentialSchemaSource.create(requiredSource, YangTextSchemaSource.class,
                        PotentialSchemaSource.Costs.LOCAL_IO.getValue()));
            }
        }
        return schemaContextFactory.createSchemaContext(requiredSources).checkedGet();
    }

}
