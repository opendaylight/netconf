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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.xpath.XPathExpressionException;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.ReceiveEventsParams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
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

    // FIXME: NETCONF-1102: this should be tied to a subscriber
    private final EventFormatterFactory<T> formatterFactory;
    private final NotificationOutputType outputType;
    private @NonNull EventFormatter<T> formatter;

    protected RestconfStream(final ListenersBroker listenersBroker, final String name,
            final ImmutableMap<EncodingName, ? extends EventFormatterFactory<T>> encodings,
            final NotificationOutputType outputType) {
        this.listenersBroker = requireNonNull(listenersBroker);
        this.name = requireNonNull(name);
        if (encodings.isEmpty()) {
            throw new IllegalArgumentException("Stream '" + name + "' must support at least one encoding");
        }
        this.encodings = encodings;

        final var encodingName = switch (outputType) {
            case JSON -> EncodingName.RFC8040_JSON;
            case XML -> EncodingName.RFC8040_XML;
        };
        this.outputType = outputType;
        formatterFactory = formatterFactory(encodingName);
        formatter = formatterFactory.emptyFormatter();
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
     * @throws IllegalAccessError if {@code encoding} is not supported
     */
    final @NonNull EventFormatterFactory<T> formatterFactory(final EncodingName encoding) {
        final var factory = encodings.get(requireNonNull(encoding));
        if (factory == null) {
            throw new IllegalArgumentException("Stream '" + name + "' does not support " + encoding);
        }
        return factory;
    }

    /**
     * Registers {@link StreamSessionHandler} subscriber.
     *
     * @param handler SSE or WS session handler.
     * @return A new {@link Registration}, or {@code null} if the subscriber cannot be added
     * @throws NullPointerException if {@code handler} is {@code null}
     */
    @Nullable Registration addSubscriber(final StreamSessionHandler handler) {
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
            local.iterator().forEachRemaining(subscriber -> subscriber.handler().endOfStream());
        }
    }


    /**
     * Post data to subscribed SSE session handlers.
     *
     * @param data Data of incoming notifications.
     */
    void post(final String data) {
        final var local = acquireSubscribers();
        if (local != null) {
            for (var sub : local) {
                final var handler = sub.handler();
                if (handler.isConnected()) {
                    handler.sendDataMessage(data);
                    LOG.debug("Data was sent to subscriber {} on connection {}:", this, handler);
                }
            }
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
     * Set query parameters for listener.
     *
     * @param params NotificationQueryParams to use.
     */
    public final void setQueryParams(final ReceiveEventsParams params) {
        final var startTime = params.startTime();
        if (startTime != null) {
            throw new RestconfDocumentedException("Stream " + name + " does not support replay",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final var leafNodes = params.leafNodesOnly();
        final var skipData = params.skipNotificationData();
        final var changedLeafNodes = params.changedLeafNodesOnly();
        final var childNodes = params.childNodesOnly();

        final var textParams = new TextParameters(
            leafNodes != null && leafNodes.value(),
            skipData != null && skipData.value(),
            changedLeafNodes != null && changedLeafNodes.value(),
            childNodes != null && childNodes.value());

        final var filter = params.filter();
        final var filterValue = filter == null ? null : filter.paramValue();

        final EventFormatter<T> newFormatter;
        if (filterValue != null && !filterValue.isEmpty()) {
            try {
                newFormatter = formatterFactory.getFormatter(textParams, filterValue);
            } catch (XPathExpressionException e) {
                throw new IllegalArgumentException("Failed to get filter", e);
            }
        } else {
            newFormatter = formatterFactory.getFormatter(textParams);
        }

        // Single assign
        formatter = newFormatter;
    }

    final @NonNull EventFormatter<T> formatter() {
        return formatter;
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
        return helper.add("name", name).add("output-type", outputType.getName());
    }
}
