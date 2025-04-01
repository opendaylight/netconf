/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.RestconfStream.Source;
import org.opendaylight.restconf.server.spi.RestconfStream.Subscription;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference base class for {@link RestconfStream.Registry} implementations.
 */
public abstract class AbstractRestconfStreamRegistry implements RestconfStream.Registry {
    /**
     * Default NETCONF stream. We follow
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.3.1">RFC 8040</a>.
     */
    private static final String DEFAULT_STREAM_NAME = "NETCONF";
    private static final String DEFAULT_STREAM_DESCRIPTION = "Default XML encoded NETCONF stream";

    /**
     * An Event Stream Filter.
     */
    @Beta
    @NonNullByDefault
    public interface EventStreamFilter {

        boolean test(YangInstanceIdentifier path, ContainerNode body);
    }

    private final class SubscriptionImpl extends AbstractRestconfStreamSubscription {
        SubscriptionImpl(final Uint32 id, final QName encoding, final String streamName, final String receiverName,
                final @Nullable EventStreamFilter filter) {
            super(id, encoding, streamName, receiverName, filter);
        }

        @Override
        protected void terminateImpl(final ServerRequest<Empty> request, final QName reason) {
            subscriptions.remove(id(), this);
            request.completeWith(Empty.value());
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
    private final ConcurrentMap<Uint32, Subscription> subscriptions = new ConcurrentHashMap<>();
    // FIXME: DTCL-driven population of these
    //    if (!mdsalService.exist(SubscriptionUtil.FILTERS.node(NodeIdentifierWithPredicates.of(
    //        StreamFilter.QNAME, SubscriptionUtil.QNAME_STREAM_FILTER_NAME, filterName))).get()) {
    private final ConcurrentMap<String, EventStreamFilter> filters = new ConcurrentHashMap<>();

    @Override
    public final RestconfStream<?> lookupStream(final String name) {
        return streams.get(requireNonNull(name));
    }

    @Override
    public final <T> void createStream(final ServerRequest<RestconfStream<T>> request, final URI restconfURI,
            final RestconfStream.Source<T> source, final String description) {
        final var stream = allocateStream(source);
        final var name = stream.name();
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must be descriptive");
        }

        Futures.addCallback(putStream(stream, description, restconfURI), new FutureCallback<>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.debug("Stream {} added", name);
                request.completeWith(stream);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed to add stream {}", name, cause);
                streams.remove(name, stream);
                request.completeWith(new RequestException("Failed to create stream " + name, cause));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public <T> void createLegacyStream(final ServerRequest<RestconfStream<T>> request, final URI restconfURI,
            final Source<T> source, final String description) {
        createStream(request, restconfURI, source, description);
    }

    @Override
    public <T> void start(final Source<T> source) {
        final var stream = new RestconfStream<>(this, source, DEFAULT_STREAM_NAME);
        streams.put(DEFAULT_STREAM_NAME, stream);
        Futures.addCallback(putStream(stream, DEFAULT_STREAM_DESCRIPTION, null), new FutureCallback<>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.debug("Default stream {} added", DEFAULT_STREAM_NAME);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed to add default stream {}", DEFAULT_STREAM_NAME, cause);
                streams.remove(DEFAULT_STREAM_NAME, stream);
            }
        }, MoreExecutors.directExecutor());
    }

    private <T> RestconfStream<T> allocateStream(final Source<T> source) {
        String name;
        RestconfStream<T> stream;
        do {
            // Use Type 4 (random) UUID. While we could just use it as a plain string, be nice to observers and anchor
            // it into UUID URN namespace as defined by RFC4122
            name = "urn:uuid:" + UUID.randomUUID().toString();
            stream = new RestconfStream<>(this, source, name);
        } while (streams.putIfAbsent(name, stream) != null);

        return stream;
    }

    protected abstract @NonNull ListenableFuture<Void> putStream(@NonNull RestconfStream<?> stream,
        @NonNull String description, @Nullable URI restconfURI);

    /**
     * Remove a particular stream and remove its entry from operational datastore.
     *
     * @param stream Stream to remove
     */
    final void removeStream(final RestconfStream<?> stream) {
        // Defensive check to see if we are still tracking the stream
        final var name = stream.name();
        if (streams.get(name) != stream) {
            LOG.warn("Stream {} does not match expected instance {}, skipping datastore update", name, stream);
            return;
        }

        Futures.addCallback(deleteStream(name), new FutureCallback<>() {
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

    protected abstract @NonNull ListenableFuture<Void> deleteStream(@NonNull String streamName);

    @Override
    public final Subscription lookupSubscription(final Uint32 id) {
        return subscriptions.get(requireNonNull(id));
    }

    @Override
    public final void establishSubscription(final ServerRequest<Subscription> request, final String streamName,
            final QName encoding, final @Nullable SubscriptionFilter filter) {
        final var stream = lookupStream(streamName);
        if (stream == null) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "%s refers to an unknown stream", streamName));
            return;
        }

        final EventStreamFilter filterImpl;
        try {
            filterImpl = resolveFilter(filter);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        final var principal = request.principal();
        final var id = Uint32.fromIntBits(prevDynamicId.incrementAndGet());
        final var subscription = new SubscriptionImpl(id, encoding, streamName,
            // FIXME: 'anonymous' instead of 'unknown' ?
            principal != null ? principal.getName() : "<unknown>",
            filterImpl);

        Futures.addCallback(createSubscription(subscription), new FutureCallback<Subscription>() {
            @Override
            public void onSuccess(final Subscription result) {
                subscriptions.put(id, result);
                request.completeWith(result);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(new RequestException(cause));
            }
        }, MoreExecutors.directExecutor());
    }

    @NonNullByDefault
    protected abstract ListenableFuture<Subscription> createSubscription(Subscription subscription);

    // FIXME: revisit
    protected void putFilter(final String name, final EventStreamFilter filter) {
        filters.put(name, filter);
    }

    protected void removeFilter(final String name) {
        filters.remove(name);
    }


    protected @Nullable EventStreamFilter resolveFilter(final @Nullable SubscriptionFilter filter)
            throws RequestException {
        return switch (filter) {
            case null -> null;
            case SubscriptionFilter.Reference(var filterName) -> getFilter(filterName);
            case SubscriptionFilter.SubtreeDefinition(var anydata) -> parseSubtreeFilter(anydata);
            case SubscriptionFilter.XPathDefinition(final var xpath) -> parseXpathFilter(xpath);
        };
    }

    @NonNullByDefault
    private EventStreamFilter getFilter(final String filterName) throws RequestException {
        final var impl = filters.get(filterName);
        if (impl != null) {
            return impl;
        }
        throw new RequestException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
            "%s refers to an unknown stream filter", filterName);
    }

    @NonNullByDefault
    private static EventStreamFilter parseSubtreeFilter(final AnydataNode<?> filter) throws RequestException {
        // FIXME: parse SubtreeDefinition anydata filter, rfc6241
        //        https://www.rfc-editor.org/rfc/rfc8650#name-filter-example
        throw new RequestException(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED,
            "Subtree filtering not implemented");
    }

    @NonNullByDefault
    private static EventStreamFilter parseXpathFilter(final String xpath) throws RequestException {
        // TODO: integrate yang-xpath-api and validate the propose xpath
        // TODO: implement XPath filter evaluation
        throw new RequestException(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED,
            "XPath filtering not implemented");
    }
}
