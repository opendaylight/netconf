/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides YANG schema sources from yang library.
 */
public final class YangLibrarySchemaYangSourceProvider implements SchemaSourceProvider<YangTextSchemaSource> {

    private static final Logger LOG = LoggerFactory.getLogger(YangLibrarySchemaYangSourceProvider.class);

    private final Map<SourceIdentifier, URL> availableSources;
    private final RemoteDeviceId id;

    public YangLibrarySchemaYangSourceProvider(
            final RemoteDeviceId id, final Map<SourceIdentifier, URL> availableSources) {
        this.id = id;
        this.availableSources = Preconditions.checkNotNull(availableSources);
    }

    @Override
    public ListenableFuture<? extends YangTextSchemaSource> getSource(
            final SourceIdentifier sourceIdentifier) {
        Preconditions.checkNotNull(sourceIdentifier);
        Preconditions.checkArgument(availableSources.containsKey(sourceIdentifier));
        return download(sourceIdentifier);
    }

    private ListenableFuture<? extends YangTextSchemaSource> download(final SourceIdentifier sourceIdentifier) {
        final URL url = availableSources.get(sourceIdentifier);
        try (InputStream in = url.openStream()) {
            final String schemaContent = new String(ByteStreams.toByteArray(in));
            final NetconfRemoteSchemaYangSourceProvider.NetconfYangTextSchemaSource yangSource =
                    new NetconfRemoteSchemaYangSourceProvider
                            .NetconfYangTextSchemaSource(id, sourceIdentifier, Optional.of(schemaContent));
            LOG.debug("Source {} downloaded from a yang library's url {}", sourceIdentifier, url);
            return Futures.immediateFuture(yangSource);
        } catch (IOException e) {
            LOG.warn("Unable to download source {} from a yang library's url {}", sourceIdentifier, url, e);
            return Futures.immediateFailedFuture(new SchemaSourceException(
                "Unable to download remote schema for " + sourceIdentifier + " from " + url, e));
        }
    }
}
