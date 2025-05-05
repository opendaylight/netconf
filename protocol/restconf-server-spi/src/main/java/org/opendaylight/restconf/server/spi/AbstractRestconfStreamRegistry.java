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
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.databind.DatabindProvider;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.restconf.server.spi.RestconfStream.Source;
import org.opendaylight.restconf.server.spi.RestconfStream.Subscription;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117.Subscription1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Encoding;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionPolicy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspendedReason;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminatedReason;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamXpathFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.dynamic.Stream1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver.State;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedAnydata;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference base class for {@link RestconfStream.Registry} implementations.
 */
public abstract class AbstractRestconfStreamRegistry implements RestconfStream.Registry {
    /**
     * {@link NodeIdentifier} of {@code leaf id} in {@link SubscriptionCompleted} et al. Value domain is all of
     * {@link Uint32} as expressed in {@link SubscriptionId}.
     */
    private static final NodeIdentifier ID_NODEID =
        NodeIdentifier.create(QName.create(SubscriptionCompleted.QNAME, "id").intern());
    /**
     * {@link NodeIdentifier} of {@code leaf stream} in {@link SubscriptionModified}. Value domain is all of
     * {@link String} as expressed in {@link Stream1#getStream()}.
     */
    private static final NodeIdentifier STREAM_NODEID =
        NodeIdentifier.create(QName.create(SubscriptionModified.QNAME, "stream").intern());
    /**
     * {@link NodeIdentifier} of {@code encoding} as expressed in {@link SubscriptionPolicy#getEncoding()}.
     */
    private static final NodeIdentifier ENCODING_NODEID = NodeIdentifier.create(Encoding.QNAME);
    /**
     * {@link NodeIdentifier} of {@code leaf reason} in {@link SubscriptionSuspended} and
     * {@link SubscriptionTerminated}. Value domains are identities derived from {@link SubscriptionSuspendedReason} and
     * {@link SubscriptionTerminatedReason}.
     */
    private static final NodeIdentifier REASON_NODEID =
            NodeIdentifier.create(QName.create(SubscriptionSuspended.QNAME, "reason").intern());
    /**
     * {@link NodeIdentifier} of {@code leaf stop-time} in {@link SubscriptionModified}. Value domain is
     * {@link String} as expressed in {@link DateAndTime}.
     */
    private static final NodeIdentifier STOP_TIME_NODEID =
            NodeIdentifier.create(QName.create(SubscriptionModified.QNAME, "stop-time").intern());
    /**
     * {@link NodeIdentifier} of {@code stream-subtree-filter} alternative in {@link FilterSpec} as expressed in
     * {@link StreamSubtreeFilter#getStreamSubtreeFilter()}.
     */
    private static final NodeIdentifier SUBTREE_FILTER_NODEID = NodeIdentifier.create(StreamSubtreeFilter.QNAME);
    /**
     * {@link NodeIdentifier} of {@link Subscription1#getUri()}.
     */
    private static final NodeIdentifier URI_NODEID = NodeIdentifier.create(
        org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117
            .YangModuleInfoImpl.qnameOf("uri"));
    /**
     * An Event Stream Filter.
     */
    @Beta
    @NonNullByDefault
    public interface EventStreamFilter {

        boolean test(YangInstanceIdentifier path, ContainerNode body);
    }

    /**
     * Subscription states. Each is backed by a notification in {@code ietf-subscribed-notifications}.
     */
    @NonNullByDefault
    private enum StateNotification {
        COMPLETED(SubscriptionCompleted.QNAME),
        RESUMED(SubscriptionResumed.QNAME),
        MODIFIED(SubscriptionModified.QNAME),
        TERMINATED(SubscriptionTerminated.QNAME),
        SUSPENDED(SubscriptionSuspended.QNAME);

        final NodeIdentifier nodeId;

        StateNotification(final QName qname) {
            nodeId = NodeIdentifier.create(qname);
        }
    }

    /**
     * Internal implementation
     * of a <a href="https://www.rfc-editor.org/rfc/rfc8639#section-2.4">dynamic subscription</a>.
     */
    private final class DynSubscription extends AbstractRestconfStreamSubscription {
        private final ConcurrentMap<String, Subscriber<DOMNotificationEvent>> receivers = new ConcurrentHashMap<>();
        private @Nullable EventStreamFilter filter;

        DynSubscription(final Uint32 id, final QName encoding, final EncodingName encodingName, final String streamName,
                final String receiverName, final TransportSession session, final @Nullable EventStreamFilter filter) {
            super(id, encoding, encodingName, streamName, receiverName, SubscriptionState.ACTIVE, session);
            this.filter = filter;
        }

        @Override
        public void addReceiver(final ServerRequest<Registration> request, final Sender sender) {
            if (state() == SubscriptionState.END) {
                LOG.debug("Subscription for id {} is not active", id());
                // TODO: this should be mapped to 404 Not Found
                request.completeWith(new RequestException("Subscription terminated"));
                return;
            }

            final var streamName = streamName();
            final var stream = streams.get(streamName);
            if (stream == null) {
                // TODO: this should never happen, really
                LOG.debug("Stream '{}' not found", streamName);
                request.completeWith(new RequestException("Subscription stream not found"));
                return;
            }

            final var session = request.session();
            if (session == null) {
                request.completeWith(new RequestException("This endpoint does not support dynamic subscriptions"));
                return;
            }

            final var receiverName = newReceiverName(session.description(), request.principal());
            if (receivers.containsKey(receiverName)) {
                request.completeWith(new RequestException("Receiver named '%s' already exists", receiverName));
                return;
            }

            final Subscriber<DOMNotificationEvent> newSubscriber;
            try {
                newSubscriber = (Subscriber<DOMNotificationEvent>) stream.addSubscriber(sender, encodingName());
            } catch (UnsupportedEncodingException e) {
                request.completeWith(new RequestException(e));
                return;
            }

            if (newSubscriber == null) {
                request.completeWith(new RequestException("Subscription stream terminated"));
                return;
            }

            receivers.put(receiverName, newSubscriber);
            Futures.addCallback(updateOperationalDatastore(), new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    request.completeWith(new AbstractRegistration() {
                        @Override
                        protected void removeRegistration() {
                            removeReceiver(receiverName, newSubscriber);
                        }
                    });
                }

                @Override
                public void onFailure(final Throwable cause) {
                    receivers.remove(receiverName, newSubscriber);
                    newSubscriber.close();
                    request.completeWith(new RequestException(cause));
                }
            }, MoreExecutors.directExecutor());
        }

        private void removeReceiver(final String receiverName, final Subscriber<?> subscriber) {
            receivers.remove(receiverName, subscriber);
            subscriber.close();
            updateOperationalDatastore();
        }

        private ListenableFuture<Void> updateOperationalDatastore() {
            return updateSubscriptionReceivers(id(), createReceivers());
        }

        @NonNullByDefault
        MapNode createReceivers() {
            final var list = new ArrayList<MapEntryNode>();
            for (var entry : receivers.entrySet()) {
                final var subscriber = entry.getValue();
                list.add(receiverNode(entry.getKey(), State.Active, subscriber.sentEventRecords(),
                    subscriber.excludedEventRecords()));
            }
            if (list.isEmpty()) {
                list.add(receiverNode(receiverName(), State.Suspended, 0, 0));
            }

            final var builder = ImmutableNodes.newSystemMapBuilder().withNodeIdentifier(RECEIVER_NODEID);
            list.forEach(builder::withChild);
            return builder.build();
        }

        @NonNullByDefault
        private static MapEntryNode receiverNode(final String receiverName, final State state, final long sentEvents,
                final long excludedEvents) {
            return ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(receiverArg(receiverName))
                .withChild(ImmutableNodes.leafNode(NAME_NODEID, receiverName))
                .withChild(ImmutableNodes.leafNode(SENT_EVENT_RECORDS_NODEID, Uint64.fromLongBits(sentEvents)))
                .withChild(ImmutableNodes.leafNode(EXCLUDED_EVENT_RECORDS_NODEID, Uint64.fromLongBits(excludedEvents)))
                .withChild(ImmutableNodes.leafNode(STATE_NODEID, state.getName()))
                .build();
        }

        @Override
        protected void terminateImpl(final ServerRequest<Empty> request, final QName reason,
                final DatabindProvider provider) {
            final var id = id();
            LOG.debug("Terminating subscription {} reason {}", id, reason);

            Futures.addCallback(removeSubscription(id()), new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.debug("Subscription {} terminated", id);
                    final var stateNotificationBody = subscriptionTerminated(id, SubscriptionTerminatedReason.QNAME);
                    publishMessage(provider.currentDatabind().modelContext(),
                        new DOMNotificationEvent.Rfc6020(stateNotificationBody, Instant.now()));
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
            final var id = id();
            LOG.debug("Subscription {} terminated due to transport session going down", id);

            Futures.addCallback(removeSubscription(id()), new FutureCallback<>() {
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
                    terminate(null, null, null);
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

        @SuppressWarnings("checkstyle:illegalCatch")
        public void publishMessage(final EffectiveModelContext modelContext, final DOMNotificationEvent input) {
            for (final var receiver : receivers.entrySet()) {
                try {
                    final var subscriber = receiver.getValue();
                    subscriber.sendDataMessage(subscriber.filter().matches(modelContext, input)
                        ? subscriber.formatter().eventData(modelContext, input, input.getEventInstant()) : null);
                } catch (Exception e) {
                    LOG.error("Failed to send notification to {}", receiver.getKey());
                }
            }
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

    protected static final QName NAME_QNAME = QName.create(StreamFilter.QNAME, "name").intern();

    protected static final NodeIdentifier EXCLUDED_EVENT_RECORDS_NODEID =
        NodeIdentifier.create(QName.create(Receiver.QNAME, "excluded-event-records"));
    protected static final NodeIdentifier NAME_NODEID = NodeIdentifier.create(NAME_QNAME);
    protected static final NodeIdentifier RECEIVER_NODEID = NodeIdentifier.create(Receiver.QNAME);
    protected static final NodeIdentifier SENT_EVENT_RECORDS_NODEID =
        NodeIdentifier.create(QName.create(Receiver.QNAME, "sent-event-records").intern());
    protected static final NodeIdentifier STATE_NODEID =
        NodeIdentifier.create(QName.create(Receiver.QNAME, "state").intern());

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
            final QName encoding, final @Nullable SubscriptionFilter filter) {
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

        final EncodingName encodingName;
        if (encoding.equals(EncodeJson$I.QNAME)) {
            encodingName = EncodingName.RFC8040_JSON;
        } else if (encoding.equals(EncodeXml$I.QNAME)) {
            encodingName = EncodingName.RFC8040_XML;
        } else {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "Encoding %s not supported", encoding));
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

        Futures.addCallback(createSubscription(id, streamName, encoding, receiverName), new FutureCallback<>() {
            @Override
            public void onSuccess(final Void result) {
                final var subscription = new DynSubscription(id, encoding, encodingName, streamName, receiverName,
                    session, filterImpl);
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
            final SubscriptionFilter filter, final DatabindProvider provider) {
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
                // FIXME handle filter, stop-time and uri
                final var stateNotificationBody = subscriptionModified(subscription.id(), subscription.streamName(),
                    subscription.encoding(), null, null, null);
                LOG.debug("Publishing subscription modified notification for ID: {}", id);
                // FIXME EffectiveModelContext should live here in abstract registry. Pass it when here to create msg
                subscription.publishMessage(provider.currentDatabind().modelContext(),
                    new DOMNotificationEvent.Rfc6020(stateNotificationBody, Instant.now()));
                request.completeWith(subscription);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(new RequestException(cause));
            }
        }, MoreExecutors.directExecutor());
    }

    @NonNullByDefault
    protected abstract ListenableFuture<@Nullable Void> createSubscription(Uint32 subscriptionId, String streamName,
        QName encoding, String receiverName);

    @NonNullByDefault
    protected abstract ListenableFuture<@Nullable Void> removeSubscription(Uint32 subscriptionId);

    @NonNullByDefault
    protected abstract ListenableFuture<@Nullable Void> modifySubscriptionFilter(Uint32 subscriptionId,
        SubscriptionFilter filter);

    @NonNullByDefault
    protected abstract ListenableFuture<@Nullable Void> updateSubscriptionReceivers(Uint32 subscriptionId,
        MapNode receivers);

    @NonNullByDefault
    protected final Map<Uint32, MapNode> currentReceivers() {
        final var result = new HashMap<Uint32, MapNode>();
        for (var subscription : subscriptions.values()) {
            if (subscription.state() != SubscriptionState.END) {
                result.put(subscription.id(), subscription.createReceivers());
            }
        }
        return result;
    }

    @NonNullByDefault
    protected static final NodeIdentifierWithPredicates receiverArg(final String receiverName) {
        return NodeIdentifierWithPredicates.of(Receiver.QNAME, NAME_QNAME, receiverName);
    }

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

    /**
     * Creates body for subscription modified state notification.
     *
     * @param id         the subscription ID
     * @param streamName the subscription stream name
     * @param encoding   the optional subscription encoding
     * @param filter     the optional subscription filter
     * @param stopTime   the optional subscription stop time
     * @param uri        the optional subscription uri
     * @return {@link ContainerNode} node with information about modified subscription
     */
    private static ContainerNode subscriptionModified(final Uint32 id, final String streamName,
            final @Nullable QName encoding, final @Nullable NormalizedAnydata filter,
            final @Nullable String stopTime, final @Nullable String uri) {
        var body = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(StateNotification.MODIFIED.nodeId)
            .withChild(ImmutableNodes.leafNode(ID_NODEID, id))
            .withChild(ImmutableNodes.leafNode(STREAM_NODEID, streamName));
        if (encoding != null) {
            body.withChild(ImmutableNodes.leafNode(ENCODING_NODEID, encoding));
        }
        // FIXME handle different filters we support
        if (filter != null) {
            body.withChild(ImmutableNodes.newAnydataBuilder(NormalizedAnydata.class)
                .withNodeIdentifier(SUBTREE_FILTER_NODEID)
                .withValue(filter)
                .build());
        }
        if (stopTime != null) {
            body.withChild(ImmutableNodes.leafNode(STOP_TIME_NODEID, stopTime));
        }
        if (uri != null) {
            body.withChild(ImmutableNodes.leafNode(URI_NODEID, uri));
        }
        return body.build();
    }

    /**
     * Creates body for state notification indicating the subscription was terminated.
     *
     * @param id          the subscription ID
     * @param errorReason the error ID associated with termination
     * @return {@link ContainerNode} notification body for subscription terminated state notification
     */
    public static ContainerNode subscriptionTerminated(final Uint32 id, final QName errorReason) {
        return getErrorStateNotification(id, errorReason, StateNotification.TERMINATED);
    }

    /**
     * Creates body for state notification indicating the subscription was suspended.
     *
     * @param id          the subscription ID
     * @param errorReason the error ID associated with suspension
     * @return {@link ContainerNode} notification body for subscription suspended state notification
     */
    public static ContainerNode subscriptionSuspended(final Uint32 id, final QName errorReason) {
        return getErrorStateNotification(id, errorReason, StateNotification.SUSPENDED);
    }

    /**
     * Builds notification body for error state notification.
     */
    private static ContainerNode getErrorStateNotification(final Uint32 id,
            final QName errorReason, final StateNotification state) {
        LOG.info("Creating {} notification for ID: {} with error ID: {}", state, id, errorReason);
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(state.nodeId)
            .withChild(ImmutableNodes.leafNode(ID_NODEID, id))
            .withChild(ImmutableNodes.leafNode(REASON_NODEID, errorReason))
            .build();
    }

    /**
     * Creates body for state notification indicating the subscription was completed.
     *
     * @param id        the subscription ID
     * @return {@link ContainerNode} notification body for subscription completed state notification
     */
    public static ContainerNode subscriptionCompleted(final Uint32 id) {
        return getStateNotification(id, StateNotification.COMPLETED);
    }

    /**
     * Creates body for state notification indicating the subscription was resumed.
     *
     * @param id        the subscription ID
     * @return {@link ContainerNode} notification body for subscription resumed state notification
     */
    public static ContainerNode subscriptionResumed(final Uint32 id) {
        return getStateNotification(id, StateNotification.RESUMED);
    }

    /**
     * Builds a generic state notification body.
     */
    private static ContainerNode getStateNotification(final Uint32 id, final StateNotification state) {
        LOG.info("Creating {} notification for ID: {}", state, id);
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(state.nodeId)
            .withChild(ImmutableNodes.leafNode(ID_NODEID, id))
            .build();
    }
}
