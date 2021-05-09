/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import org.opendaylight.netconf.sal.connect.netconf.schema.NetconfRemoteSchemaYangSourceProvider.NetconfYangTextSchemaSource;
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

    public YangLibrarySchemaYangSourceProvider(final RemoteDeviceId id,
            final Map<SourceIdentifier, URL> availableSources) {
        this.id = id;
        this.availableSources = ImmutableMap.copyOf(availableSources);
    }

    @Override
    public ListenableFuture<? extends YangTextSchemaSource> getSource(final SourceIdentifier sourceIdentifier) {
        final URL url = availableSources.get(requireNonNull(sourceIdentifier));
        checkArgument(url != null);
        try (InputStream in = url.openStream()) {
            final byte[] schemaContent = in.readAllBytes();
            final NetconfYangTextSchemaSource yangSource = new NetconfYangTextSchemaSource(id, sourceIdentifier,
                url.toString(), schemaContent);
            LOG.debug("Source {} downloaded from a yang library's url {}", sourceIdentifier, url);
            return Futures.immediateFuture(yangSource);
        } catch (IOException e) {
            LOG.warn("Unable to download source {} from a yang library's url {}", sourceIdentifier, url, e);
            return Futures.immediateFailedFuture(new SchemaSourceException(
                "Unable to download remote schema for " + sourceIdentifier + " from " + url, e));
        }
    }
}
