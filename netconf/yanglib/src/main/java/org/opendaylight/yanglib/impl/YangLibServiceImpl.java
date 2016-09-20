/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yanglib.impl;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.CheckedFuture;
import java.io.IOException;
import org.opendaylight.yanglib.api.YangLibService;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides schema sources from yang library
 */
public class YangLibServiceImpl implements YangLibService {
    private static final Logger LOG = LoggerFactory.getLogger(YangLibServiceImpl.class);

    private SchemaRepository schemaRepository;

    public YangLibServiceImpl() {

    }

    public void setSchemaRepository(final SchemaRepository schemaRepository) {
        LOG.debug("Setting schema repository {}", schemaRepository);
        this.schemaRepository = schemaRepository;
    }

    @Override
    public String getSchema(final String name, final String revision) {
        Preconditions.checkNotNull(schemaRepository, "Schema repository is not initialized");
        LOG.debug("Attempting load for schema source {}:{}", name, revision);
        final SourceIdentifier sourceId =
                RevisionSourceIdentifier.create(name, Optional.fromNullable(revision.equals("") ? null : revision));

        final CheckedFuture<YangTextSchemaSource, SchemaSourceException> sourceFuture =
                schemaRepository.getSchemaSource(sourceId, YangTextSchemaSource.class);

        try {
            final YangTextSchemaSource source = sourceFuture.checkedGet();
            return new String(ByteStreams.toByteArray(source.openStream()));
        } catch (SchemaSourceException|IOException e) {
            throw new IllegalStateException("Unable to get schema" + sourceId, e);
        }
    }
}
