/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.FilterParameter;
import org.opendaylight.restconf.nb.rfc8040.StartTimeParameter;
import org.opendaylight.restconf.nb.rfc8040.StopTimeParameter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;

/**
 * Features of query parameters part of both notifications.
 */
abstract class AbstractQueryParams extends AbstractNotificationsData {
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4).appendLiteral('-')
        .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
        .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('T')
        .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendOffset("+HH:MM", "Z").toFormatter();

    // FIXME: these should be final
    private Instant start = null;
    private Instant stop = null;
    private boolean leafNodesOnly = false;
    private boolean skipNotificationData = false;

    public final Instant getStart() {
        return start;
    }

    /**
     * Set query parameters for listener.
     *
     * @param startTime     Start-time of getting notification.
     * @param stopTime      Stop-time of getting notification.
     * @param filter        Indicates which subset of all possible events are of interest.
     * @param leafNodesOnly If TRUE, notifications will contain changes of leaf nodes only.
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public final void setQueryParams(final StartTimeParameter startTime, final StopTimeParameter stopTime,
            final FilterParameter filter, final boolean leafNodesOnly, final boolean skipNotificationData) {
        start = startTime == null ? Instant.now() : parseDateAndTime(startTime.value());
        stop = stopTime == null ? null : parseDateAndTime(stopTime.value());
        this.leafNodesOnly = leafNodesOnly;
        this.skipNotificationData = skipNotificationData;

        if (filter != null) {
            try {
                setFilter(filter.uriValue());
            } catch (XPathExpressionException e) {
                throw new IllegalArgumentException("Failed to get filter", e);
            }
        }
    }

    abstract void setFilter(@Nullable String xpathString) throws XPathExpressionException;

    /**
     * Parse input of query parameters - start-time or stop-time - from {@link DateAndTime} format
     * to {@link Instant} format.
     *
     * @param uriValue Start-time or stop-time as string in {@link DateAndTime} format.
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

    /**
     * Check whether this query should only notify about leaf node changes.
     *
     * @return true if this query should only notify about leaf node changes
     */
    boolean getLeafNodesOnly() {
        return leafNodesOnly;
    }

    /**
     * Check whether this query should notify changes without data.
     *
     * @return true if this query should notify about changes with  data
     */
    public boolean isSkipNotificationData() {
        return skipNotificationData;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    <T extends BaseListenerInterface> boolean checkStartStop(final Instant now, final T listener) {
        if (stop != null) {
            if (start.compareTo(now) < 0 && stop.compareTo(now) > 0) {
                return true;
            }
            if (stop.compareTo(now) < 0) {
                try {
                    listener.close();
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
}
