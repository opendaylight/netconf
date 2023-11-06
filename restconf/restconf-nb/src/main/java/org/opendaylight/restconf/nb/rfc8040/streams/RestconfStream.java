/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableMap;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
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
public abstract class RestconfStream<T> {
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

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStream.class);
    private static final VarHandle SUBSCRIBERS;

    static {
        try {
            SUBSCRIBERS = MethodHandles.lookup().findVarHandle(RestconfStream.class, "subscribers", Subscribers.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // ImmutableMap because it retains iteration order
    private final @NonNull ImmutableMap<EncodingName, ? extends EventFormatterFactory<T>> encodings;
    private final @NonNull ListenersBroker listenersBroker;
    private final @NonNull String name;

    // Accessed via SUBSCRIBERS, 'null' indicates we have been shut down
    @SuppressWarnings("unused")
    private volatile Subscribers<T> subscribers = Subscribers.empty();

    private Registration registration;

    protected RestconfStream(final ListenersBroker listenersBroker, final String name,
            final ImmutableMap<EncodingName, ? extends EventFormatterFactory<T>> encodings) {
        this.listenersBroker = requireNonNull(listenersBroker);
        this.name = requireNonNull(name);
        if (encodings.isEmpty()) {
            throw new IllegalArgumentException("Stream '" + name + "' must support at least one encoding");
        }
        this.encodings = encodings;
    }

    /**
     * Get name of stream.
     *
     * @return Stream name.
     */
    public final @NonNull String name() {
        return name;
    }

    /**
     * Get supported {@link EncodingName}s. The set is guaranteed to contain at least one element and does not contain
     * {@code null}s.
     *
     * @return Supported encodings.
     */
    @SuppressWarnings("null")
    final @NonNull Set<EncodingName> encodings() {
        return encodings.keySet();
    }

    /**
     * Return the {@link EventFormatterFactory} for an encoding.
     *
     * @param encoding An {@link EncodingName}
     * @return The {@link EventFormatterFactory} for the selected encoding
     * @throws NullPointerException if {@code encoding} is {@code null}
     * @throws UnsupportedEncodingException if {@code encoding} is not supported
     */
    final @NonNull EventFormatterFactory<T> formatterFactory(final EncodingName encoding)
            throws UnsupportedEncodingException {
        final var factory = encodings.get(requireNonNull(encoding));
        if (factory == null) {
            throw new UnsupportedEncodingException("Stream '" + name + "' does not support " + encoding);
        }
        return factory;
    }

    /**
     * Registers {@link StreamSessionHandler} subscriber.
     *
     * @param handler SSE or WS session handler.
     * @param encoding Requested event stream encoding
     * @return A new {@link Registration}, or {@code null} if the subscriber cannot be added
     * @throws NullPointerException if any argument is {@code null}
     */
    @Nullable Registration addSubscriber(final StreamSessionHandler handler, final EncodingName encoding)
            throws UnsupportedEncodingException {
        final var factory = formatterFactory(encoding);


        // Lockless add of a subscriber. If we observe a null this stream is dead before the new subscriber could be
        // added.
        final var toAdd = new Subscriber<>(this, handler, formatter);
        var observed = acquireSubscribers();
        while (observed != null) {
            final var next = observed.add(toAdd);
            final var witness = (Subscribers<T>) SUBSCRIBERS.compareAndExchangeRelease(this, observed, next);
            if (witness == observed) {
                LOG.debug("Subscriber {} is added.", handler);
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

    /**
     * Signal the end-of-stream condition to subscribers, shut down this stream and initiate its removal from global
     * state.
     */
    final void endOfStream() {
        // Atomic assertion we are ending plus locked clean up
        final var local = (Subscribers<T>) SUBSCRIBERS.getAndSetRelease(this, null);
        if (local != null) {
            terminate();
            local.endOfStream();
        }
    }

    /**
     * Post data to subscribed SSE session handlers.
     *
     * @param modelContext An {@link EffectiveModelContext} used to format the input
     * @param input Input data
     * @param now Current time
     * @throws NullPointerException if any argument is {@code null}
     */
    void sendDataMessage(final EffectiveModelContext modelContext, final T input, final Instant now) {
        final var local = acquireSubscribers();
        if (local != null) {
            local.publish(modelContext, input, now);
        } else {
            LOG.debug("Ignoring sendDataMessage() on terminated stream {}", this);
        }
    }

    private void terminate() {
        if (registration != null) {
            registration.close();
            registration = null;
        }
        listenersBroker.removeStream(this);
    }

    /**
     * Sets {@link Registration} registration.
     *
     * @param registration a listener registration registration.
     */
    @Holding("this")
    final void setRegistration(final Registration registration) {
        this.registration = requireNonNull(registration);
    }

    /**
     * Checks if {@link Registration} registration exists.
     *
     * @return {@code true} if exists, {@code false} otherwise.
     */
    @Holding("this")
    final boolean isListening() {
        return registration != null;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("name", name);
    }
}
