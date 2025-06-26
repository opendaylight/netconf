/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry.EventStreamFilter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver.State;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single subscriber to an {@link RestconfStream}.
 *
 * @param <T> event type
 */
@NonNullByDefault
abstract sealed class Subscriber<T> extends AbstractRegistration {
    /**
     * A {@link Subscriber} established via the <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.3">RFC8040</a>
     * mechanism.
     *
     * @param <T> event type
     */
    static final class Rfc8040Subscriber<T> extends Subscriber<T> {
        Rfc8040Subscriber(final RestconfStream<T> stream, final Sender sender, final EventFormatter<T> formatter,
                final EventFilter<T> filter) {
            super(stream, sender, formatter, filter);
        }

        @Override
        void sendDataMessage(final @Nullable String data) {
            if (data != null) {
                sender().sendDataMessage(data);
            }
        }
    }

    /**
     * A {@link Subscriber} established via the <a href="https://www.rfc-editor.org/rfc/rfc8639#section-2.6">RFC8639</a>
     * mechanism.
     *
     * @param <T> event type
     */
    static final class Rfc8639Subscriber<T> extends Subscriber<T> {
        private static final Logger LOG = LoggerFactory.getLogger(Rfc8639Subscriber.class);
        private static final VarHandle EER_VH;
        private static final VarHandle SER_VH;

        static {
            final var lookup = MethodHandles.lookup();
            try {
                EER_VH = lookup.findVarHandle(Rfc8639Subscriber.class, "excludedEventRecords", long.class);
                SER_VH = lookup.findVarHandle(Rfc8639Subscriber.class, "sentEventRecords", long.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final String receiverName;

        @SuppressFBWarnings(value = "UUF_UNUSED_FIELD")
        private long excludedEventRecords;
        @SuppressFBWarnings(value = "UUF_UNUSED_FIELD")
        private long sentEventRecords;
        private State receiverState;

        Rfc8639Subscriber(final RestconfStream<T> stream, final Sender sender, final EventFormatter<T> formatter,
                final EventFilter<T> filter, final String receiverName, final State receiverState) {
            super(stream, sender, formatter, filter);
            this.receiverName = requireNonNull(receiverName);
            this.receiverState = requireNonNull(receiverState);
        }

        /**
         * {@inheritDoc}
         *
         * <p>This implementation sends data only if receiver state is {@code Active}, otherwise data is not send.
         */
        @Override
        void sendDataMessage(final @Nullable String data) {
            if (receiverState() == Receiver.State.Active) {
                LOG.debug("Receiver {} is active, sending data", receiverName);
                sendMessage(data);
            } else {
                LOG.debug("Receiver {} is not Active, sending data blocked", receiverName);
            }
        }

        /**
         * This method sends data regardless of subscription state.
         *
         * @param data Data to send.
         */
        void sendMessage(final @Nullable String data) {
            final VarHandle vh;
            if (data != null) {
                sender().sendDataMessage(data);
                vh = SER_VH;
            } else {
                vh = EER_VH;
            }
            vh.getAndAdd(this, 1L);
        }

        String receiverName() {
            return receiverName;
        }

        State receiverState() {
            return receiverState;
        }

        Uint64 excludedEventRecords() {
            return readCounter(EER_VH);
        }

        Uint64 sentEventRecords() {
            return readCounter(SER_VH);
        }

        private Uint64 readCounter(final VarHandle vh) {
            return Uint64.fromLongBits((long) vh.getAcquire(this));
        }

        public void replaceEventStreamFilter(final EventStreamFilter newEventStreamFilter) {
            swapFilter((EventFilter<T>) newEventStreamFilter);
        }

        public void setReceiverState(final State newState) {
            receiverState = requireNonNull(newState);
        }
    }

    private final RestconfStream<T> stream;
    private final EventFormatter<T> formatter;
    private final Sender sender;

    private volatile EventFilter<T> filter;

    Subscriber(final RestconfStream<T> stream, final Sender sender, final EventFormatter<T> formatter,
            final EventFilter<T> filter) {
        this.stream = requireNonNull(stream);
        this.sender = requireNonNull(sender);
        this.formatter = requireNonNull(formatter);
        this.filter = requireNonNull(filter);
    }

    final EventFilter<T> filter() {
        return filter;
    }

    final EventFormatter<T> formatter() {
        return formatter;
    }

    final Sender sender() {
        return sender;
    }

    protected final void swapFilter(final EventFilter<? super T> newFilter) {
        this.filter = requireNonNull((EventFilter<T>) newFilter);
    }

    /**
     * Method to send data message to subscriber.
     *
     * @param data Data to send.
     */
    abstract void sendDataMessage(@Nullable String data);

    final void endOfStream() {
        sender.endOfStream();
    }

    @Override
    protected final void removeRegistration() {
        stream.removeSubscriber(this);
    }
}
