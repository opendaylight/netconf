/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.xml.xpath.XPathExpressionException;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.ReceiveEventsParams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Features of subscribing part of both notifications.
 */
abstract class AbstractCommonSubscriber<T> extends AbstractNotificationsData implements BaseListenerInterface {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommonSubscriber.class);
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4).appendLiteral('-')
        .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
        .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('T')
        .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendOffset("+HH:MM", "Z").toFormatter();

    private final EventFormatterFactory<T> formatterFactory;
    private final NotificationOutputType outputType;
    private final String streamName;
    protected final @NonNull ListenersBroker listenersBroker;

    @GuardedBy("this")
    private final Set<StreamSessionHandler> subscribers = new HashSet<>();
    @GuardedBy("this")
    private Registration registration;

    // FIXME: these should be final
    private @NonNull EventFormatter<T> formatter;
    private Instant start = null;
    private Instant stop = null;

    AbstractCommonSubscriber(final String streamName, final NotificationOutputType outputType,
            final EventFormatterFactory<T> formatterFactory, final ListenersBroker listenersBroker) {
        this.streamName = requireNonNull(streamName);
        checkArgument(!streamName.isEmpty());

        this.outputType = requireNonNull(outputType);
        this.formatterFactory = requireNonNull(formatterFactory);
        this.listenersBroker = requireNonNull(listenersBroker);
        formatter = formatterFactory.emptyFormatter();
    }

    @Override
    public final String getStreamName() {
        return streamName;
    }

    @Override
    public final String getOutputType() {
        return outputType.getName();
    }

    @Override
    public final synchronized boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    @Override
    public final synchronized Set<StreamSessionHandler> getSubscribers() {
        return new HashSet<>(subscribers);
    }

    @Override
    public final synchronized void close() throws InterruptedException, ExecutionException {
        if (registration != null) {
            registration.close();
            registration = null;
        }
        deleteDataInDS(streamName).get();
        subscribers.clear();
    }

    @Override
    public synchronized void addSubscriber(final StreamSessionHandler subscriber) {
        final boolean isConnected = subscriber.isConnected();
        checkState(isConnected);
        LOG.debug("Subscriber {} is added.", subscriber);
        subscribers.add(subscriber);
    }

    @Override
    public synchronized void removeSubscriber(final StreamSessionHandler subscriber) {
        subscribers.remove(subscriber);
        LOG.debug("Subscriber {} is removed", subscriber);
        if (!hasSubscribers()) {
            listenersBroker.removeAndCloseListener(this);
        }
    }

    public final Instant getStart() {
        return start;
    }

    /**
     * Set query parameters for listener.
     *
     * @param params NotificationQueryParams to use.
     */
    public final void setQueryParams(final ReceiveEventsParams params) {
        final var startTime = params.startTime();
        start = startTime == null ? Instant.now() : parseDateAndTime(startTime.value());

        final var stopTime = params.stopTime();
        stop = stopTime == null ? null : parseDateAndTime(stopTime.value());

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

    /**
     * Post data to subscribed SSE session handlers.
     *
     * @param data Data of incoming notifications.
     */
    synchronized void post(final String data) {
        final Iterator<StreamSessionHandler> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            final StreamSessionHandler subscriber = iterator.next();
            final boolean isConnected = subscriber.isConnected();
            if (isConnected) {
                subscriber.sendDataMessage(data);
                LOG.debug("Data was sent to subscriber {} on connection {}:", this, subscriber);
            } else {
                // removal is probably not necessary, because it will be removed explicitly soon after invocation of
                // onWebSocketClosed(..) in handler; but just to be sure ...
                iterator.remove();
                LOG.debug("Subscriber for {} was removed - web-socket session is not open.", this);
            }
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    final boolean checkStartStop(final Instant now) {
        if (stop != null) {
            if (start.compareTo(now) < 0 && stop.compareTo(now) > 0) {
                return true;
            }
            if (stop.compareTo(now) < 0) {
                try {
                    close();
                } catch (final Exception e) {
                    throw new RestconfDocumentedException("Problem with unregister listener." + e);
                }
            }
        } else if (start != null) {
            if (start.compareTo(now) < 0) {
                start = null;
                return true;
            }
        } else {
            return true;
        }
        return false;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("stream-name", streamName).add("output-type", getOutputType());
    }

    /**
     * Parse input of query parameters - start-time or stop-time - from {@link DateAndTime} format
     * to {@link Instant} format.
     *
     * @param dateAndTime Start-time or stop-time as {@link DateAndTime} object.
     * @return Parsed {@link Instant} by entry.
     */
    private static @NonNull Instant parseDateAndTime(final DateAndTime dateAndTime) {
        final TemporalAccessor accessor;
        try {
            accessor = FORMATTER.parse(dateAndTime.getValue());
        } catch (final DateTimeParseException e) {
            throw new RestconfDocumentedException("Cannot parse of value in date: " + dateAndTime, e);
        }
        return Instant.from(accessor);
    }
}
