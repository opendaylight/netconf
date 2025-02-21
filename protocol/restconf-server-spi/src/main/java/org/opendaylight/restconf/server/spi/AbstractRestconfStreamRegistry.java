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
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.RestconfStream.Subscription;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference base class for {@link RestconfStream.Registry} implementations.
 */
public abstract class AbstractRestconfStreamRegistry implements RestconfStream.Registry {
    private static final class SubscriptionImpl extends AbstractRestconfStreamSubscription {
        SubscriptionImpl(final Uint32 id, final QName encoding, final String streamName, final String receiverName,
                final @Nullable String filterName) {
            super(id, encoding, streamName, receiverName, filterName);
        }

        @Override
        protected void terminateImpl(final ServerRequest<Empty> request, final QName reason) {
            // FIXME: id tracking: remove this ID from the pool of used IDs
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRestconfStreamRegistry.class);

    /**
     * Previous dynamic subscription ID. We follow
     * <a href="https://www.rfc-editor.org/rfc/rfc8639.html#section-6>Implementation Considerations</a> here:
     *
     * <blockquote>
     *   A best practice is to use the lower half of the "id"
     *   object's integer space when that "id" is assigned by an external
     *   entity (such as with a configured subscription).  This leaves the
     *   upper half of the subscription integer space available to be
     *   dynamically assigned by the publisher.
     * </blockquote>
     */
    private final AtomicInteger prevDynamicId = new AtomicInteger(Integer.MAX_VALUE);
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

    protected abstract @NonNull ListenableFuture<Void> putStream(@NonNull MapEntryNode stream);

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
            new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {
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

    protected abstract @NonNull ListenableFuture<Void> deleteStream(@NonNull NodeIdentifierWithPredicates streamName);

    @Override
    public final void establishSubscription(final ServerRequest<Subscription> request, final String streamName,
            final QName encoding, final @Nullable String filterName) {
        final var stream = lookupStream(streamName);
        if (stream == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "%s refers to an unknown stream", streamName));
            return;
        }

        final var principal = request.principal();
        final var subscription = new SubscriptionImpl(Uint32.fromIntBits(prevDynamicId.incrementAndGet()), encoding,
            streamName,
            // FIXME: 'anonymous' instead of 'unknown' ?
            principal != null ? principal.getName() : "<unknown>",
            filterName);

        Futures.addCallback(createSubscription(subscription), new FutureCallback<Subscription>() {
            @Override
            public void onSuccess(final Subscription result) {
                request.completeWith(result);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(new ServerException(cause));
            }
        }, MoreExecutors.directExecutor());
    }

    @NonNullByDefault
    protected abstract ListenableFuture<Subscription> createSubscription(Subscription subscription);
}
