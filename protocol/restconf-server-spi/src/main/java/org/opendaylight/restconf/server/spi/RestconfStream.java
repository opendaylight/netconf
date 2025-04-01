/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF notification event stream. Each stream produces a number of events encoded in at least one encoding. The
 * set of supported encodings is available through {@link #encodings()}.
 *
 * @param <T> Type of processed events
 */
public final class RestconfStream<T> {
    /**
     * An opinionated view on what values we can produce for {@code Access.getEncoding()}. The name can only be composed
     * of one or more characters matching {@code [a-zA-Z]}.
     *
     * @param name Encoding name, as visible via the stream's {@code access} list
     */
    // FIXME: reconcile with RFC8639
    public record EncodingName(@NonNull String name) {
        private static final Pattern PATTERN = Pattern.compile("[a-zA-Z]+");

        /**
         * Well-known JSON encoding defined by RFC8040's {@code ietf-restconf-monitoring.yang}.
         */
        public static final @NonNull EncodingName RFC8040_JSON = new EncodingName("json");
        /**
         * Well-known XML encoding defined by RFC8040's {@code ietf-restconf-monitoring.yang}.
         */
        public static final @NonNull EncodingName RFC8040_XML = new EncodingName("xml");

        public EncodingName {
            if (!PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("name must match " + PATTERN);
            }
        }
    }

    /**
     * A service which knows where instantiated streams can be located. This is typically tied to a server endpoint
     * providing access to <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.2">RFC8040 event streams</a>.
     */
    @NonNullByDefault
    public interface LocationProvider {
        /**
         * Return the base location URL of the streams service based on request URI.
         *
         * @param restconfURI request base URI, with trailing slash
         * @return base location URL
         */
        URI baseStreamLocation(URI restconfURI);
    }

    /**
     * A sink of events for a {@link RestconfStream}.
     */
    public interface Sink<T> {
        /**
         * Publish a set of events generated from input data.
         *
         * @param modelContext An {@link EffectiveModelContext} used to format the input
         * @param input Input data
         * @param now Current time
         * @throws NullPointerException if any argument is {@code null}
         */
        void publish(EffectiveModelContext modelContext, T input, Instant now);

        /**
         * Called when the stream has reached its end.
         */
        void endOfStream();
    }

    /**
     * A source of events for a {@link RestconfStream}.
     */
    public abstract static class Source<T> {
        // ImmutableMap because it retains iteration order
        final @NonNull ImmutableMap<EncodingName, ? extends EventFormatterFactory<T>> encodings;

        protected Source(final ImmutableMap<EncodingName, ? extends EventFormatterFactory<T>> encodings) {
            if (encodings.isEmpty()) {
                throw new IllegalArgumentException("A source must support at least one encoding");
            }
            this.encodings = encodings;
        }

        protected abstract @NonNull Registration start(Sink<T> sink);

        @Override
        public final String toString() {
            return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
        }

        protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return helper.add("encodings", encodings.keySet());
        }
    }

    /**
     * Interface for session handler that is responsible for sending of data over established session.
     */
    @NonNullByDefault
    public interface Sender {
        /**
         * Interface for sending String message through one of implementation.
         *
         * @param data Message data to be send.
         */
        void sendDataMessage(String data);

        /**
         * Called when the stream has reached its end. The handler should close all underlying resources.
         */
        void endOfStream();
    }

    /**
     * An entity managing allocation and lookup of {@link RestconfStream}s.
     */
    public interface Registry {
        /**
         * Get a {@link RestconfStream} by its name.
         *
         * @param name Stream name.
         * @return A {@link RestconfStream}, or {@code null} if the stream with specified name does not exist.
         * @throws NullPointerException if {@code name} is {@code null}
         */
        @Nullable RestconfStream<?> lookupStream(String name);

        /**
         * Create a {@link RestconfStream} with a unique name. This method will atomically generate a stream name,
         * create the corresponding instance and register it.
         *
         * @param <T> Stream type
         * @param request {@link ServerRequest} for this invocation
         * @param restconfURI resolved {@code {+restconf}} resource name
         * @param source Stream instance
         * @param description Stream descriptiion
         * @throws NullPointerException if any argument is {@code null}
         */
        <T> void createStream(ServerRequest<RestconfStream<T>> request, URI restconfURI, Source<T> source,
            String description);

        /**
         * Create a legacy {@link RestconfStream} with a unique name. This method will atomically generate a stream
         * name, create the corresponding instance and register it.
         * Difference compared to normal {@link RestconfStream} created by {@link Registry#createStream} is that legacy
         * stream is automatically closed after last {@link Subscriber} is removed.
         *
         * @param <T> Stream type
         * @param request {@link ServerRequest} for this invocation
         * @param restconfURI resolved {@code {+restconf}} resource name
         * @param source Stream instance
         * @param description Stream descriptiion
         * @throws NullPointerException if any argument is {@code null}
         */
        @Deprecated(since = "9.0.0", forRemoval = true)
        <T> void createLegacyStream(ServerRequest<RestconfStream<T>> request, URI restconfURI, Source<T> source,
            String description);

        /**
         * Create default {@link RestconfStream} with a predefined name.
         *
         * <p>This method will create the corresponding instance and register it.
         *
         * @param <T> Stream type
         * @param source Stream instance
         * @throws NullPointerException if any argument is {@code null}
         */
        <T> void start(Source<T> source);

        /**
         * Establish a new RFC8639 subscription to a stream.
         *
         * @param request {@link ServerRequest} for this invocation
         * @param encoding requested encoding
         * @param streamName requested stream name
         * @param filter optional filter
         * @throws NullPointerException if {@code encoding} or {@code streamName} is {@code null}
         */
        @NonNullByDefault
        void establishSubscription(ServerRequest<Subscription> request, String streamName, QName encoding,
            @Nullable SubscriptionFilter filter);

        /**
         * Modify existing RFC8639 subscription to a stream.
         *
         * @param request {@link ServerRequest} for this invocation
         * @param id of subscription
         * @param filter new filter
         * @throws NullPointerException if {@code request}, {@code id} or {@code filter} is {@code null}
         */
        @NonNullByDefault
        void modifySubscription(ServerRequest<Subscription> request, Uint32 id, SubscriptionFilter filter);

        /**
         * Lookup an existing subscription.
         *
         * @param id subscription ID
         * @return A {@link Subscription}, or {@code null} if the stream with specified name does not exist.
         */
        @Nullable Subscription lookupSubscription(Uint32 id);

        /**
         * Update the counter value for a specific receiver in the operational datastore.
         *
         * <p>This method writes an updated counter for the receiver identified by the provided {@code ReceiverHolder}.
         * The type of counter to update is specified by the {@code recordType} parameter. The update is performed on
         * the operational datastore via a merge operation, and the method returns a {@link ListenableFuture}
         * that completes when the commit succeeds or fails.
         *
         * @param receiver   the {@link ReceiverHolder} containing the subscription ID and receiver name
         * @param recordType the type of counter record to update (e.g. sent-event-records or excluded-event-records)
         */
        ListenableFuture<Void> updateReceiver(ReceiverHolder receiver, long counter,
            ReceiverHolder.RecordType recordType);
    }

    /**
     * A handle to a RFC8639 subscription.
     */
    // TODO: a .toOperational() should result in the equivalent MapEntryNode equivalent of a Binding Subscription
    @Beta
    public abstract static sealed class Subscription
            permits AbstractRestconfStreamSubscription, ForwardingRestconfStreamSubscription {
        @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD",
            justification = "https://github.com/spotbugs/spotbugs/issues/2749")
        private volatile QName terminated;

        /**
         * Returns the {@code subscription id}.
         *
         * @return the {@code subscription id}
         */
        @NonNullByDefault
        public abstract Uint32 id();

        /**
         * Returns the {@code receiver name}.
         *
         * @return the {@code receiver name}
         */
        @NonNullByDefault
        public abstract String receiverName();

        /**
         * Returns the encoding.
         *
         * @return the encoding
         */
        @NonNullByDefault
        public abstract QName encoding();

        /**
         * Returns the {@code stream name}.
         *
         * @return the {@code stream name}
         */
        @NonNullByDefault
        public abstract String streamName();

        @NonNullByDefault
        public final void terminate(final ServerRequest<Empty> request, final QName reason) {
            final var witness = (QName) TERMINATED_VH.compareAndExchangeRelease(this, null, requireNonNull(reason));
            if (witness != null) {
                request.completeWith(new RequestException("Subscription already terminated with " + witness));
                return;
            }

            LOG.debug("Terminating subscription {} due to {}", id(), reason);
            terminateImpl(request, reason);
        }

        @NonNullByDefault
        protected abstract void terminateImpl(ServerRequest<Empty> request, QName reason);

        @Override
        public final String toString() {
            return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
        }

        @NonNullByDefault
        protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return helper.add("terminated", terminated);
        }
    }

    /**
     * A subscription filter. It can be either one of
     * <ul>
     *   <li>a {@code SubscriptionFilter.Reference}, which needs to be resolved, or<li>
     *   <li>a {@code SubscriptionFilter.SubtreeDefinition}, where we need to parse a document chunk, or</li>
     *   <li>a {@code SubscriptionFilter.XPathDefinition}, where we need to parse an XPath expression</li>
     * </ul>
     */
    @NonNullByDefault
    public sealed interface SubscriptionFilter {
        /**
         * A reference to an externally-provided filter.
         *
         * @param filterName filter name
         */
        record Reference(String filterName) implements SubscriptionFilter {
            public Reference {
                requireNonNull(filterName);
            }
        }

        /**
         * A subtree filter definition, provided as an {@link AnydataNode} document.
         *
         * @param document the subtree filter
         */
        record SubtreeDefinition(AnydataNode<?> document) implements SubscriptionFilter {
            public SubtreeDefinition {
                requireNonNull(document);
            }
        }

        /**
         * A XPath filter definition, provided as a string containing the filter expression.
         *
         * @param xpath the XPath filter expression
         */
        // FIXME: this should be already parsed to yang-xpath-api
        record XPathDefinition(String xpath) implements SubscriptionFilter {
            public XPathDefinition {
                requireNonNull(xpath);
            }
        }
    }

    public sealed interface Receiver permits ReceiverHolder {
        /**
         * Increments the sent-event-records counter and writes the updated value to the MD-SAL datastore.
         */
        void updateSentEventRecord();

        /**
         * Increments the excluded-event-records counter and writes the updated value to the MD-SAL datastore.
         */
        void updateExcludedEventRecord();

        /**
         * Returns the {@code receiver state}.
         */
        State state();

        /**
         * Returns the {@code receiver name}.
         */
        String receiverName();

        /**
         * Returns the {@code subscription id}.
         */
        String subscriptionId();

        void setState(final State newState);

        /**
         * Returns the {@code sent event counter}.
         */
        AtomicLong sentEventCounter();

        /**
         * Returns the {@code excluded event counter}.
         */
        AtomicLong excludedEventCounter();

        enum State {
            ACTIVE,
            SUSPENDED,
            CONNECTING, //check if we support
            DISCONNECTED //check if we support
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStream.class);
    private static final VarHandle SUBSCRIBERS_VH;
    private static final VarHandle TERMINATED_VH;

    static {
        final var lookup = MethodHandles.lookup();
        try {
            SUBSCRIBERS_VH = lookup.findVarHandle(RestconfStream.class, "subscribers", Subscribers.class);
            TERMINATED_VH = lookup.findVarHandle(Subscription.class, "terminated", QName.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull Sink<T> sink = new Sink<>() {
        @Override
        public void publish(final EffectiveModelContext modelContext, final T input, final Instant now) {
            final var local = acquireSubscribers();
            if (local != null) {
                local.publish(modelContext, input, now);
            } else {
                LOG.debug("Ignoring publish() on terminated stream {}", RestconfStream.this);
            }
        }

        @Override
        public void endOfStream() {
            // Atomic assertion we are ending plus guarded cleanup
            final var local = (Subscribers<T>) SUBSCRIBERS_VH.getAndSetRelease(RestconfStream.this, null);
            if (local != null) {
                terminate();
                local.endOfStream();
            }
        }
    };

    private final @NonNull AbstractRestconfStreamRegistry registry;
    private final @NonNull Source<T> source;
    private final @NonNull String name;

    // Accessed via SUBSCRIBERS, 'null' indicates we have been shut down
    @SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile Subscribers<T> subscribers = Subscribers.empty();

    private Registration registration;

    public RestconfStream(final AbstractRestconfStreamRegistry registry, final Source<T> source, final String name) {
        this.registry = requireNonNull(registry);
        this.source = requireNonNull(source);
        this.name = requireNonNull(name);
    }

    /**
     * Get name of stream.
     *
     * @return Stream name.
     */
    public @NonNull String name() {
        return name;
    }

    /**
     * Get supported {@link EncodingName}s. The set is guaranteed to contain at least one element and does not contain
     * {@code null}s.
     *
     * @return Supported encodings.
     */
    @SuppressWarnings("null")
    public @NonNull Set<EncodingName> encodings() {
        return source.encodings.keySet();
    }

    /**
     * Registers {@link Sender} subscriber.
     *
     * @param handler SSE session handler.
     * @param encoding Requested event stream encoding
     * @param params Reception parameters
     * @return A new {@link Registration}, or {@code null} if the subscriber cannot be added
     * @throws NullPointerException if any argument is {@code null}
     * @throws UnsupportedEncodingException if {@code encoding} is not supported
     * @throws XPathExpressionException if requested filter is not valid
     */
    public @Nullable Registration addSubscriber(final Sender handler, final EncodingName encoding,
            final EventStreamGetParams params) throws UnsupportedEncodingException, XPathExpressionException {
        final var factory = source.encodings.get(requireNonNull(encoding));
        if (factory == null) {
            throw new UnsupportedEncodingException("Stream '" + name + "' does not support " + encoding);
        }

        final var startTime = params.startTime();
        if (startTime != null) {
            throw new IllegalArgumentException("Stream " + name + " does not support replay");
        }

        final var leafNodes = params.leafNodesOnly() != null && params.leafNodesOnly().value();
        final var skipData = params.skipNotificationData() != null && params.skipNotificationData().value();
        final var changedLeafNodes = params.changedLeafNodesOnly() != null && params.changedLeafNodesOnly().value();
        final var childNodes = params.childNodesOnly() != null && params.childNodesOnly().value();

        final var textParams = new TextParameters(leafNodes, skipData, changedLeafNodes, childNodes);

        final var filter = params.filter();
        final var filterValue = filter == null ? null : filter.paramValue();
        final var formatter = filterValue == null || filterValue.isEmpty() ? factory.getFormatter(textParams)
            : factory.getFormatter(textParams, filterValue);


        // Lockless add of a subscriber. If we observe a null this stream is dead before the new subscriber could be
        // added.
        final var toAdd = new Subscriber<>(this, handler, formatter);
        var observed = acquireSubscribers();
        while (observed != null) {
            final var next = observed.add(toAdd);
            final var witness = (Subscribers<T>) SUBSCRIBERS_VH.compareAndExchangeRelease(this, observed, next);
            if (witness == observed) {
                LOG.debug("Subscriber {} is added.", handler);
                if (observed instanceof Subscribers.Empty) {
                    // We have became non-empty, start the source
                    startSource();
                }
                return toAdd;
            }

            // We have raced: retry the operation
            observed = witness;
        }

        return null;
    }

    /**
     * Removes a {@link Subscriber}. If this was the last subscriber also shut down this stream and initiate its removal
     * from global state.
     *
     * @param subscriber The {@link Subscriber} to remove
     * @throws NullPointerException if {@code subscriber} is {@code null}
     */
    void removeSubscriber(final Subscriber<T> subscriber) {
        final var toRemove = requireNonNull(subscriber);
        var observed = acquireSubscribers();
        while (observed != null) {
            final var next = observed.remove(toRemove);
            final var witness = (Subscribers<T>) SUBSCRIBERS_VH.compareAndExchangeRelease(this, observed, next);
            if (witness == observed) {
                LOG.debug("Subscriber {} is removed", subscriber);
                if (next == null) {
                    // We have lost the last subscriber, terminate.
                    terminate();
                }
                return;
            }

            // We have raced: retry the operation
            observed = witness;
        }
    }

    private Subscribers<T> acquireSubscribers() {
        return (Subscribers<T>) SUBSCRIBERS_VH.getAcquire(this);
    }

    private void startSource() {
        // We have not started the stream yet, make sure that happens. This is a bit more involved, as the source may
        // immediately issue endOfStream(), which in turn invokes terminate(). But at that point start() has not return
        // and therefore registration is still null -- and thus we need to see if we are still on-line.
        final var reg = source.start(sink);
        synchronized (this) {
            if (acquireSubscribers() == null) {
                reg.close();
            } else {
                registration = reg;
            }
        }
    }

    private void terminate() {
        synchronized (this) {
            if (registration != null) {
                registration.close();
                registration = null;
            }
        }
        registry.removeStream(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", name).add("source", source).toString();
    }
}
