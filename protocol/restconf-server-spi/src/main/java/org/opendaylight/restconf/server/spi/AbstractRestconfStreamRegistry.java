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
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.RestconfStream.Source;
import org.opendaylight.restconf.server.spi.RestconfStream.Subscription;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamXpathFilter;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference base class for {@link RestconfStream.Registry} implementations.
 */
public abstract class AbstractRestconfStreamRegistry implements RestconfStream.Registry {
    /**
     * An Event Stream Filter.
     */
    @Beta
    @NonNullByDefault
    public interface EventStreamFilter {

        boolean test(YangInstanceIdentifier path, ContainerNode body);
    }

    /**
     * Internal implementation
     * of a <a href="https://www.rfc-editor.org/rfc/rfc8639#section-2.4">dynamic subscription</a>.
     */
    private final class DynSubscription extends AbstractRestconfStreamSubscription {

        private @Nullable EventStreamFilter filter;
        private @Nullable final Instant stopTime;

        DynSubscription(final Uint32 id, final QName encoding, final String streamName, final String receiverName,
                final TransportSession session, final @Nullable EventStreamFilter filter,
                final SubscriptionStateService stateService, final @Nullable Instant stopTime) {
            super(id, encoding, streamName, receiverName, SubscriptionState.ACTIVE, session, stateService, stopTime);
            this.filter = filter;
            this.stopTime = stopTime;
        }

        @Override
        protected void terminateImpl(final ServerRequest<Empty> request, final QName reason) {
            stopTimerTask();
            final var id = id();
            LOG.debug("Terminating subscription {} reason {}", id, reason);

            Futures.addCallback(removeSubscription(id()), new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.debug("Subscription {} terminated", id);
                    subscriptions.remove(id, DynSubscription.this);
                    request.completeWith(Empty.value());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Cannot terminate subscription {}", id, cause);
                    request.completeWith(new RequestException(cause));
                }
            }, MoreExecutors.directExecutor());
        }

        private void channelClosed() {
            stopTimerTask();
            final var id = id();
            LOG.debug("Subscription {} terminated due to transport session going down", id);

            Futures.addCallback(removeSubscription(id()), new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.debug("Subscription {} cleaned up", id);
                    subscriptions.remove(id);
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Subscription {} failed to clean up", id, cause);
                    subscriptions.remove(id);
                }
            }, MoreExecutors.directExecutor());
        }

        void controlSessionClosed() {
            switch (state()) {
                case END -> {
                    LOG.debug("Subscription id:{} already in END state during attempt to end it", id());
                    terminate(null, null);
                }
                default -> {
                    setState(RestconfStream.SubscriptionState.END);
                    channelClosed();
                }
            }
        }

        void setFilter(final EventStreamFilter newFilter) {
            filter = newFilter;
        }

        @Override
        protected @Nullable EventStreamFilter filter() {
            return filter;
        }

        @Override
        public @Nullable Instant stopTime() {
            return stopTime;
        }

        @Override
        protected void stopTimeRemoveSubscription() {
            final var id = id();
            LOG.debug("Subscription {} suspended after configured stop-time was reached", id);

            Futures.addCallback(removeSubscription(id()), new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.debug("Subscription {} cleaned up", id);
                    subscriptions.remove(id);
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Subscription {} failed to clean up", id, cause);
                    subscriptions.remove(id);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    private static final class DynSubscriptionResource extends AbstractRegistration {
        private final DynSubscription subscription;

        DynSubscriptionResource(final DynSubscription subscription) {
            this.subscription = requireNonNull(subscription);
        }

        @Override
        protected void removeRegistration() {
            subscription.controlSessionClosed();
        }

        @Override
        protected ToStringHelper addToStringAttributes(final ToStringHelper toStringHelper) {
            return super.addToStringAttributes(toStringHelper.add("subscription", subscription.id()));
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRestconfStreamRegistry.class);
    /**
     * Default NETCONF stream. We follow
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.3.1">RFC 8040</a>.
     */
    private static final String DEFAULT_STREAM_NAME = "NETCONF";
    private static final String DEFAULT_STREAM_DESCRIPTION = "Default XML encoded NETCONF stream";

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
    private final ConcurrentMap<Uint32, DynSubscription> subscriptions = new ConcurrentHashMap<>();
    // FIXME: This is not quite sufficient and should be split into two maps:
    //          1. filterSpecs, which is a HashMap<String, ChoiceNode> recording known filter-spec definitions
    //             access should be guarded by a lock
    //          2. filters, which is a volatile ImmutableMap<String, EventStreamFilter>
    //             - lookups should go through getAcquire()
    //             - updates should hold a lock and publish new versions via setRelease()
    //        The distinction is crucial, as we need to recompile filters when EffectiveModelContext changes and update
    //        filters accordingly.
    // FIXME: There is also a missing piece: we are not populating the operational datastore. This needs to be addressed
    //        after we address the above, so that anytime we update filters, we issue a callout to subclass to update
    //        oper so that it contains those filterSpecs for which we have EventStreamFilters. Those that failed to
    //        parse need to be removed.
    //        The end result should be that for given *configured* filters we report *operational* filters that are
    //        really in use.
    //        Note: the MD-SAL implementation needs to use a Cluster Singleton Service to ensure oper updates are
    //              happening from a single node only.
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
    @Deprecated(since = "9.0.0", forRemoval = true)
    public <T> void createLegacyStream(final ServerRequest<RestconfStream<T>> request, final URI restconfURI,
            final Source<T> source, final String description) {
        createStream(request, restconfURI, source, description);
    }

    /**
     * Create default {@link RestconfStream} with a predefined name.
     *
     * <p>This method will create the corresponding instance and register it.
     *
     * @param <T> Stream type
     * @param source Stream instance
     * @throws NullPointerException if any argument is {@code null}
     */
    protected final <T> void start(final Source<T> source) {
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
    public final void establishSubscription(final ServerRequest<Uint32> request, final String streamName,
            final QName encoding, final @Nullable SubscriptionFilter filter,
            final SubscriptionStateService stateService, final @Nullable Instant stopTime) {
        final var session = request.session();
        if (session == null) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED,
                "This end point does not support dynamic subscriptions."));
            return;
        }

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

        final var id = Uint32.fromIntBits(prevDynamicId.incrementAndGet());
        final var receiverName = newReceiverName(session.description(), request.principal());

        Futures.addCallback(createSubscription(id, streamName, encoding, receiverName, String.valueOf(stopTime)),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    final var subscription = new DynSubscription(id, encoding, streamName, receiverName, session,
                        filterImpl, stateService, stopTime);
                    subscriptions.put(id, subscription);
                    session.registerResource(new DynSubscriptionResource(subscription));
                    request.completeWith(id);
                }

                @Override
                public void onFailure(final Throwable cause) {
                    request.completeWith(new RequestException(cause));
                }
            }, MoreExecutors.directExecutor());
    }

    @NonNullByDefault
    private static String newReceiverName(final TransportSession.Description description,
            final @Nullable Principal principal) {
        final var receiverName = description.toFriendlyString();
        return principal == null ? receiverName : principal.getName() + " via " + receiverName;
    }

    @Override
    public void modifySubscription(final ServerRequest<Subscription> request, final Uint32 id,
            final SubscriptionFilter filter) {
        final var subscription = subscriptions.get(id);
        if (subscription == null) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "There is no subscription with given ID."));
            return;
        }

        final EventStreamFilter filterImpl;
        try {
            filterImpl = resolveFilter(filter);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        Futures.addCallback(modifySubscriptionFilter(id, filter), new FutureCallback<>() {
            @Override
            public void onSuccess(final Void result) {
                subscription.setFilter(filterImpl);
                request.completeWith(subscription);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(new RequestException(cause));
            }
        }, MoreExecutors.directExecutor());
    }

    @NonNullByDefault
    protected abstract ListenableFuture<Void> createSubscription(Uint32 subscriptionId, String streamName,
        QName encoding, String receiverName, String stopTime);

    @NonNullByDefault
    protected abstract ListenableFuture<Void> removeSubscription(Uint32 subscriptionId);

    @NonNullByDefault
    protected abstract ListenableFuture<Void> modifySubscriptionFilter(Uint32 subscriptionId,
        SubscriptionFilter filter);

    /**
     * Bulk-update the known filter definitions.
     *
     * @param nameToSpec the update map, {@code null} values indicate removals
     */
    @NonNullByDefault
    protected final void updateFilterDefinitions(final Map<String, @Nullable ChoiceNode> nameToSpec) {
        for (var entry : nameToSpec.entrySet()) {
            final var filterName = entry.getKey();
            final var filterSpec = entry.getValue();
            if (filterSpec == null) {
                filters.remove(filterName);
                LOG.debug("Removed filter {} without specification", filterName);
                continue;
            }

            final EventStreamFilter filter;
            try {
                filter = parseFilter(filterSpec);
            } catch (RequestException e) {
                filters.remove(filterName);
                LOG.warn("Removed filter {} due to parse failure", filterSpec.prettyTree(), e);
                continue;
            }

            filters.put(filterName, filter);
            LOG.debug("Updated filter {} to {}", filterName, filter);
        }
    }

    @NonNullByDefault
    private EventStreamFilter parseFilter(final ChoiceNode filterSpec) throws RequestException {
        final var subtree = (AnydataNode<?>) filterSpec.childByArg(new NodeIdentifier(StreamSubtreeFilter.QNAME));
        if (subtree != null) {
            return parseSubtreeFilter(subtree);
        }
        final var xpath = (LeafNode<?>) filterSpec.childByArg(new NodeIdentifier(StreamXpathFilter.QNAME));
        if (xpath != null) {
            return parseXpathFilter((String) xpath.body());
        }
        throw new RequestException("Unsupported filter %s", filterSpec);
    }

    private @Nullable EventStreamFilter resolveFilter(final @Nullable SubscriptionFilter filter)
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
    protected abstract EventStreamFilter parseSubtreeFilter(AnydataNode<?> filter) throws RequestException;

    @NonNullByDefault
    private static EventStreamFilter parseXpathFilter(final String xpath) throws RequestException {
        // TODO: integrate yang-xpath-api and validate the propose xpath
        // TODO: implement XPath filter evaluation
        throw new RequestException(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED,
            "XPath filtering not implemented");
    }
}
