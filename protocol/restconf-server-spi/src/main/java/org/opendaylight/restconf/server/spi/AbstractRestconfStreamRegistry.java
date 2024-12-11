/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference base class for {@link RestconfStream.Registry} implementations.
 */
public abstract class AbstractRestconfStreamRegistry implements RestconfStream.Registry {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractRestconfStreamRegistry.class);

    private final ConcurrentMap<String, RestconfStream<?>> streams = new ConcurrentHashMap<>();
    private final QName nameQname;
    private final QName streamQname;

    protected AbstractRestconfStreamRegistry(final QName streamQname) {
        this.streamQname = requireNonNull(streamQname);
        nameQname = QName.create(streamQname, "name").intern();
    }

    @Override
    public final @Nullable RestconfStream<?> lookupStream(final String name) {
        return streams.get(requireNonNull(name));
    }

    protected RestconfStream<?> registerStream(final String name, final RestconfStream<?> stream) {
        return streams.putIfAbsent(name, stream);
    }

    protected void unregisterStream(final String name, final RestconfStream<?> stream) {
        streams.remove(name, stream);
    }

    protected abstract @NonNull ListenableFuture<?> putStream(@NonNull MapEntryNode stream);

    /**
     * Remove a particular stream and remove its entry from operational datastore.
     *
     * @param stream Stream to remove
     */
    public void removeStream(final RestconfStream<?> stream) {
        // Defensive check to see if we are still tracking the stream
        final var name = stream.name();
        if (streams.get(name) != stream) {
            LOG.warn("Stream {} does not match expected instance {}, skipping datastore update", name, stream);
            return;
        }

        Futures.addCallback(deleteStream(NodeIdentifierWithPredicates.of(streamQname, nameQname, name)),
            new FutureCallback<Object>() {
                @Override
                public void onSuccess(final Object result) {
                    LOG.debug("Stream {} removed", name);
                    streams.remove(name, stream);
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed to remove stream {}, operational datastore may be inconsistent", name, cause);
                    streams.remove(name, stream);
                }
            }, MoreExecutors.directExecutor());
    }

    protected abstract @NonNull ListenableFuture<?> deleteStream(@NonNull NodeIdentifierWithPredicates streamName);
}
