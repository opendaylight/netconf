/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides YANG schema sources from YANG library. The set of available sources is pre-determined when this provider
 * is created, but each source is acquired on demand.
 */
public final class LibrarySchemaSourceProvider implements SchemaSourceProvider<YangTextSchemaSource> {
    private static final Logger LOG = LoggerFactory.getLogger(LibrarySchemaSourceProvider.class);

    private final ImmutableMap<SourceIdentifier, URL> availableSources;
    private final RemoteDeviceId id;

    public LibrarySchemaSourceProvider(final RemoteDeviceId id, final Map<SourceIdentifier, URL> availableSources) {
        this.id = requireNonNull(id);
        this.availableSources = ImmutableMap.copyOf(availableSources);
    }

    @Override
    public ListenableFuture<? extends YangTextSchemaSource> getSource(final SourceIdentifier sourceIdentifier) {
        final var url = availableSources.get(requireNonNull(sourceIdentifier));
        checkArgument(url != null);

        final byte[] schemaContent;
        try (var in = url.openStream()) {
            schemaContent = in.readAllBytes();
        } catch (IOException e) {
            LOG.warn("Unable to download source {} from a yang library's url {}", sourceIdentifier, url, e);
            return Futures.immediateFailedFuture(new SchemaSourceException(
                "Unable to download remote schema for " + sourceIdentifier + " from " + url, e));
        }

        final var yangSource = new CachedYangTextSchemaSource(id, sourceIdentifier, url.toString(),
            new String(schemaContent, StandardCharsets.UTF_8));
        LOG.debug("Source {} downloaded from a yang library's url {}", sourceIdentifier, url);
        return Futures.immediateFuture(yangSource);
    }
}
