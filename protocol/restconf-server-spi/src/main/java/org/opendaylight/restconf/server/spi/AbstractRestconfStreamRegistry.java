/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.YangModuleInfoImpl.qnameOf;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.restconf.server.spi.RestconfStream.Source;
import org.opendaylight.restconf.server.spi.RestconfStream.Subscription;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.restconf.server.spi.Subscriber.Rfc8639Subscriber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Encoding;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamXpathFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.SubscriptionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.ReceiversBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver.State;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.ReceiverBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.ZeroBasedCounter64;
import org.opendaylight.yangtools.binding.util.BindingMap;
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
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
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
        private final Set<Rfc8639Subscriber<?>> receivers = ConcurrentHashMap.newKeySet();

        private @Nullable EventStreamFilter filter;

        DynSubscription(final Uint32 id, final QName encoding, final EncodingName encodingName, final String streamName,
                final String receiverName, final TransportSession session, final @Nullable EventStreamFilter filter,
                final @Nullable Instant stopTime) {
            super(id, encoding, encodingName, streamName, receiverName, SubscriptionState.ACTIVE, session, stopTime);
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

            final Rfc8639Subscriber<?> newSubscriber;
            try {
                newSubscriber = stream.addSubscriber(sender, encodingName(),
                    newReceiverName(session.description(), request.principal()), filter());
            } catch (UnsupportedEncodingException e) {
                request.completeWith(new RequestException(e));
                return;
            }

            if (newSubscriber == null) {
                request.completeWith(new RequestException("Subscription stream terminated"));
                return;
            }

            receivers.add(newSubscriber);
            Futures.addCallback(updateOperationalDatastore(), new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    request.completeWith(new AbstractRegistration() {
                        @Override
                        protected void removeRegistration() {
                            removeReceiver(newSubscriber);
                        }
                    });
                }

                @Override
                public void onFailure(final Throwable cause) {
                    receivers.remove(newSubscriber);
                    newSubscriber.close();
                    request.completeWith(new RequestException(cause));
                }
            }, MoreExecutors.directExecutor());
        }

        private void removeReceiver(final Rfc8639Subscriber<?> subscriber) {
            receivers.remove(subscriber);
            subscriber.close();
            updateOperationalDatastore();
        }

        private ListenableFuture<Void> updateOperationalDatastore() {
            if (terminated() != null) {
                // it is possible this Subscription was already terminated, in which case we don't want to
                // update datastore as whole subscription should already be deleted.
                LOG.debug("Ignoring operational datastore update for terminated subscription {}", id());
                return Futures.immediateVoidFuture();
            }
            return updateSubscriptionReceivers(id(), createReceivers());
        }

        @NonNullByDefault
        MapNode createReceivers() {
            final var list = new ArrayList<MapEntryNode>();
            for (var subscriber : receivers) {
                list.add(receiverNode(subscriber.receiverName(), State.Active, subscriber.sentEventRecords(),
                    subscriber.excludedEventRecords()));
            }
            if (list.isEmpty()) {
                list.add(receiverNode(receiverName(), State.Suspended, Uint64.ZERO, Uint64.ZERO));
            }

            final var builder = ImmutableNodes.newSystemMapBuilder().withNodeIdentifier(RECEIVER_NODEID);
            list.forEach(builder::withChild);
            return builder.build();
        }

        @NonNullByDefault
        private static MapEntryNode receiverNode(final String receiverName, final State state, final Uint64 sentEvents,
                final Uint64 excludedEvents) {
            return ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(receiverArg(receiverName))
                .withChild(ImmutableNodes.leafNode(NAME_NODEID, receiverName))
                .withChild(ImmutableNodes.leafNode(SENT_EVENT_RECORDS_NODEID, sentEvents))
                .withChild(ImmutableNodes.leafNode(EXCLUDED_EVENT_RECORDS_NODEID, excludedEvents))
                .withChild(ImmutableNodes.leafNode(STATE_NODEID, state.getName()))
                .build();
        }

        @Override
        protected void terminateImpl(final ServerRequest<Empty> request, final QName reason) {
            final var id = id();
            LOG.debug("Terminating subscription {} reason {}", id, reason);

            Futures.addCallback(removeSubscription(id()), new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.debug("Subscription {} terminated", id);
                    subscriptions.remove(id, DynSubscription.this);
                    subscriptionRemoved(id);
                    receivers.forEach(Subscriber::endOfStream);
                    request.completeWith(Empty.value());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Cannot terminate subscription {}", id, cause);
                    request.completeWith(new RequestException(cause));
                }
            }, MoreExecutors.directExecutor());
        }

        @Override
        public void publishStateNotif(final String message) {
            for (final var subscriber : receivers) {
                subscriber.sendDataMessage(message);
            }
        }

        private void channelClosed() {
            final var id = id();
            LOG.debug("Subscription {} terminated due to transport session going down", id);
            deleteSubscription(id);
            subscriptionRemoved(id);
        }

        @Override
        void stopTimeRemoveSubscription() {
            final var id = id();
            LOG.debug("Subscription {} terminated after configured stop-time was reached", id);
            deleteSubscription(id);
        }

        private void deleteSubscription(final Uint32 id) {
            Futures.addCallback(removeSubscription(id), new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.debug("Subscription {} cleaned up", id);
                    receivers.forEach(Subscriber::endOfStream);
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
            for (final var receiver : receivers) {
                receiver.setEventStreamFilter(newFilter);
            }
        }

        @Override
        protected @Nullable EventStreamFilter filter() {
            return filter;
        }

        @Override
        public List<String> receiversNames() {
            return receivers.stream()
                .map(Rfc8639Subscriber::receiverName)
                .toList();
        }

        @Override
        public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909
                .subscriptions.Subscription toOperational() {
            final Encoding encoding;
            if (EncodeXml$I.QNAME.equals(encoding())) {
                encoding = EncodeXml$I.VALUE;
            } else if (EncodeJson$I.QNAME.equals(encoding())) {
                encoding = EncodeJson$I.VALUE;
            } else {
                throw new IllegalArgumentException("Invalid encoding: " + encoding().getLocalName());
            }

            final var subscriptionBuilder = new SubscriptionBuilder()
                .setId(new SubscriptionId(id()))
                .setEncoding(encoding)
                .setReceivers(new ReceiversBuilder()
                    .setReceiver(receivers.stream()
                        .map(receiver -> new ReceiverBuilder()
                            .setName(receiver.receiverName())
                            .setState(State.Active)
                            .setSentEventRecords(new ZeroBasedCounter64(receiver.sentEventRecords()))
                            .setExcludedEventRecords(new ZeroBasedCounter64(receiver.excludedEventRecords()))
                            .build())
                        .collect(BindingMap.toMap()))
                    .build());

            if (stopTime() != null) {
                subscriptionBuilder.setStopTime(new DateAndTime(stopTime().toString()));
            }
            return subscriptionBuilder.build();
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
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    /**
     * Default NETCONF stream. We follow
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.3.1">RFC 8040</a>.
     */
    private static final String DEFAULT_STREAM_NAME = "NETCONF";
    private static final String DEFAULT_STREAM_DESCRIPTION = "Default XML encoded NETCONF stream";

    protected static final QName NAME_QNAME = qnameOf("name");

    protected static final NodeIdentifier EXCLUDED_EVENT_RECORDS_NODEID =
        NodeIdentifier.create(qnameOf("excluded-event-records"));
    protected static final NodeIdentifier NAME_NODEID = NodeIdentifier.create(NAME_QNAME);
    protected static final NodeIdentifier RECEIVER_NODEID = NodeIdentifier.create(Receiver.QNAME);
    protected static final NodeIdentifier SENT_EVENT_RECORDS_NODEID =
        NodeIdentifier.create(qnameOf("sent-event-records"));
    protected static final NodeIdentifier STATE_NODEID = NodeIdentifier.create(qnameOf("state"));

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

    private volatile @Nullable Future<?> stopTimeTask;
    private volatile @Nullable Uint32 nextSubscriptionToStop;
    private volatile @Nullable Instant nextStopTime;

    @Override
    public final RestconfStream<?> lookupStream(final String name) {
        return streams.get(requireNonNull(name));
    }

    @Override
    public final <T> void createStream(final ServerRequest<RestconfStream<T>> request, final URI restconfURI,
            final Source<T> source, final String description) {
        final var name = allocateStreamName();
        registerStream(request, restconfURI, description, new RestconfStream<>(this, source, name));
    }

    @Override
    @Deprecated(since = "9.0.0", forRemoval = true)
    public final <T> void createLegacyStream(final ServerRequest<RestconfStream<T>> request, final URI restconfURI,
            final Source<T> source, final String description) {
        final var name = allocateStreamName();
        registerStream(request, restconfURI, description, new LegacyRestconfStream<>(this, source, name));
    }

    private <T> void registerStream(final ServerRequest<RestconfStream<T>> request, final URI restconfURI,
            final String description, final RestconfStream<T> stream) {
        final var name = stream.name();
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must be descriptive");
        }
        streams.put(name, stream);

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

    private String allocateStreamName() {
        String name;
        do {
            // Use Type 4 (random) UUID. While we could just use it as a plain string, be nice to observers and anchor
            // it into UUID URN namespace as defined by RFC4122
            name = "urn:uuid:" + UUID.randomUUID();
        } while (streams.containsKey(name));
        return name;
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
            final QName encoding, final @Nullable SubscriptionFilter filter, final @Nullable Instant stopTime) {
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

        if (stopTime != null && !stopTime.isAfter(Instant.now())) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "Stop-time must be in future."));
            return;
        }

        final var id = Uint32.fromIntBits(prevDynamicId.incrementAndGet());
        final var receiverName = newReceiverName(session.description(), request.principal());

        Futures.addCallback(createSubscription(id, streamName, encoding, receiverName, stopTime),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    final var subscription = new DynSubscription(id, encoding, encodingName, streamName, receiverName,
                        session, filterImpl, stopTime);
                    subscriptions.put(id, subscription);
                    if (stopTime != null) {
                        initiateStopTime(id, stopTime);
                    }
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
            final SubscriptionFilter filter, final Instant stopTime) {
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

        if (stopTime != null && !stopTime.isAfter(Instant.now())) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "Stop-time must be in future."));
            return;
        }

        Futures.addCallback(modifySubscriptionParameters(id, filter, stopTime), new FutureCallback<>() {
            @Override
            public void onSuccess(final Void result) {
                subscription.setFilter(filterImpl);
                // Ignore stop-time if it is null
                if (stopTime != null) {
                    subscription.updateStopTime(stopTime);
                    subscriptionModified(id, stopTime);
                }
                request.completeWith(subscription);
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(new RequestException(cause));
            }
        }, MoreExecutors.directExecutor());
    }

    private static Future<?> schedule(final Runnable task, final long delay) {
        return VIRTUAL_THREAD_EXECUTOR.submit(() -> {
            final var singleThreadScheduler = Executors.newSingleThreadScheduledExecutor();

            try (singleThreadScheduler) {
                singleThreadScheduler.schedule(task, delay, TimeUnit.SECONDS);
            }
        });
    }

    /**
     * Schedule stop task if none is running or if the given stopTime is earlier than the current one.
     *
     * @param id the subscription ID for which the stop time is being scheduled
     * @param stopTime the stop time of the subscription
     */
    @NonNullByDefault
    private synchronized void initiateStopTime(final Uint32 id, final Instant stopTime) {
        if (stopTimeTask == null || nextStopTime == null || stopTime.isBefore(nextStopTime)) {
            scheduleStopTimeTask(id, stopTime);
        }
    }

    /**
     * Cancel any existing scheduled stop task and schedule a new stop task for the given stop time.
     */
    private void scheduleStopTimeTask(final Uint32 id, final Instant stopTime) {
        if (stopTimeTask != null) {
            stopTimeTask.cancel(false);
            stopTimeTask = null;
        }

        nextSubscriptionToStop = id;
        nextStopTime = stopTime;
        stopTimeTask = schedule(this::executeStopTimeTask, Duration.between(Instant.now(), nextStopTime).toSeconds());
    }

    /**
     * The task executed when the scheduled stop time is reached.
     */
    private synchronized void executeStopTimeTask() {
        final var subscription = lookupSubscription(nextSubscriptionToStop);
        if (subscription != null) {
            subscription.stopTimeReached();
        }
        nextSubscriptionToStop = null;
        scheduleNextStopTimeTask();
    }

    /**
     * Finds the subscription with the earliest stop time and schedules the stop task for it.
     */
    private void scheduleNextStopTimeTask() {
        Instant minimalTime = null;
        Uint32 id = null;
        for (final var subscription : subscriptions.entrySet()) {
            final var stopTime = subscription.getValue().stopTime();
            if (stopTime != null && (minimalTime == null || minimalTime.isAfter(stopTime))) {
                minimalTime = stopTime;
                id = subscription.getKey();
            }
        }
        if (id != null) {
            scheduleStopTimeTask(id, minimalTime);
        } else {
            nextSubscriptionToStop = null;
            nextStopTime = null;
            if (stopTimeTask != null) {
                stopTimeTask.cancel(false);
                stopTimeTask = null;
            }
        }
    }

    /**
     * Called when a subscription is removed.
     *
     * <p>If the removed subscription was the next scheduled to stop, reschedules the stop task.
     *
     * @param id the ID of the subscription that was removed
     */
    private synchronized void subscriptionRemoved(final Uint32 id) {
        if (id.equals(nextSubscriptionToStop)) {
            nextSubscriptionToStop = null;
            nextStopTime = null;
            if (stopTimeTask != null) {
                stopTimeTask.cancel(true);
                stopTimeTask = null;
            }
            scheduleNextStopTimeTask();
        }
    }

    /**
     * Handle updates to a subscription's stop time.
     *
     * <p>If there is no scheduled task, this method schedules a new one.
     * If a task is already scheduled, it decides whether to reschedule based on the new stop time.
     *
     * @param id the ID of the subscription being modified
     * @param newStopTime the updated stop time of the subscription; must not be null
     */
    @NonNullByDefault
    private synchronized void subscriptionModified(final Uint32 id, final Instant newStopTime) {
        if (stopTimeTask == null || nextStopTime == null || newStopTime.isBefore(nextStopTime)) {
            scheduleStopTimeTask(id, newStopTime);
        } else if (id.equals(nextSubscriptionToStop) && newStopTime.isAfter(nextStopTime)) {
            scheduleNextStopTimeTask();
        }
    }

    @NonNullByDefault
    protected abstract ListenableFuture<@Nullable Void> createSubscription(Uint32 subscriptionId, String streamName,
        QName encoding, String receiverName, @Nullable Instant stopTime);

    @NonNullByDefault
    protected abstract ListenableFuture<@Nullable Void> removeSubscription(Uint32 subscriptionId);

    @NonNullByDefault
    protected abstract ListenableFuture<Void> modifySubscriptionParameters(Uint32 subscriptionId,
        SubscriptionFilter filter, @Nullable Instant stopTime);

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
}
