/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.xpath.XPathExpressionException;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yangtools.concepts.Registration;
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
     * An opinionated view on what values we can produce for {@link Access#getEncoding()}. The name can only be composed
     * of one or more characters matching {@code [a-zA-Z]}.
     *
     * @param name Encoding name, as visible via the stream's {@code access} list
     */
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
         * @param restconfURI resolved {@code {+restconf}} resource name
         * @param source Stream instance
         * @param description Stream descriptiion
         * @return A future {@link RestconfStream} instance
         * @throws NullPointerException if any argument is {@code null}
         */
        <T> @NonNull RestconfFuture<RestconfStream<T>> createStream(URI restconfURI, Source<T> source,
            String description);
    }

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStream.class);
    private static final VarHandle SUBSCRIBERS;

    static {
        try {
            SUBSCRIBERS = MethodHandles.lookup().findVarHandle(RestconfStream.class, "subscribers", Subscribers.class);
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
            final var local = (Subscribers<T>) SUBSCRIBERS.getAndSetRelease(RestconfStream.this, null);
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
    @SuppressWarnings("unused")
    @SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile Subscribers<T> subscribers = Subscribers.empty();

    @GuardedBy("this")
    private Registration registration;

    RestconfStream(final AbstractRestconfStreamRegistry registry, final Source<T> source, final String name) {
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
    @NonNull Set<EncodingName> encodings() {
        return source.encodings.keySet();
    }

    /**
     * Registers {@link Sender} subscriber.
     *
     * @param handler SSE or WS session handler.
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
            final var witness = (Subscribers<T>) SUBSCRIBERS.compareAndExchangeRelease(this, observed, next);
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
            final var witness = (Subscribers<T>) SUBSCRIBERS.compareAndExchangeRelease(this, observed, next);
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
        return (Subscribers<T>) SUBSCRIBERS.getAcquire(this);
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
