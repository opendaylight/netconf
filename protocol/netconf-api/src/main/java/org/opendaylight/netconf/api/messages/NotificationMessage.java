/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.text.ParsePosition;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * Special kind of netconf message that contains a timestamp.
 */
public final class NotificationMessage extends NetconfMessage {
    public static final @NonNull String ELEMENT_NAME = "notification";

    private static final Logger LOG = LoggerFactory.getLogger(NotificationMessage.class);

    /**
     * Used for unknown/un-parse-able event-times.
     */
    // FIXME: we should differentiate unknown and invalid event times
    public static final Instant UNKNOWN_EVENT_TIME = Instant.EPOCH;

    /**
     * The ISO-like date-time formatter that formats or parses a date-time with
     * the offset and zone if available, such as '2011-12-03T10:15:30',
     * '2011-12-03T10:15:30+01:00' or '2011-12-03T10:15:30+01:00[Europe/Paris]'.
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Provide a {@link String} representation of a {@link Instant} object,
     * using the time-zone offset for UTC, {@code ZoneOffset.UTC}.
     */
    public static final Function<Instant, String> RFC3339_DATE_FORMATTER = date ->
            DATE_TIME_FORMATTER.format(date.atOffset(ZoneOffset.UTC));

    /**
     * Parse a {@link String} object into a {@link Instant} using the time-zone offset for UTC, {@code ZoneOffset.UTC},
     * and the system default time-zone, {@code ZoneId.systemDefault()}.
     *
     * <p>While parsing, if an exception occurs, we try to handle it as if it is due to a leap second. If that is
     * the case, a simple conversion is applied, replacing the second-of-minute of 60 with 59. If that is not the case,
     * we propagate the {@link DateTimeParseException} to the caller.
     */
    public static final Function<String, Instant> RFC3339_DATE_PARSER = time -> {
        try {
            return ZonedDateTime.parse(time, DATE_TIME_FORMATTER).toInstant();
        } catch (DateTimeParseException exception) {
            final var res = handlePotentialLeapSecond(time);
            if (res != null) {
                return res;
            }
            throw exception;
        }
    };

    /**
     * Check whether the input {@link String} is representing a time compliant with the ISO
     * format and having a leap second; e.g. formatted as 23:59:60. If that's the case, a simple
     * conversion is applied, replacing the second-of-minute of 60 with 59.
     *
     * @param time {@link String} representation of a time
     * @return {@code null} if time isn't ISO compliant or if the time doesn't have a leap second else an
     *         {@link Instant} as per as the RFC3339_DATE_PARSER.
     */
    private static Instant handlePotentialLeapSecond(final String time) {
        // Parse the string from offset 0, so we get the whole value.
        final int offset = 0;
        final var parsed = DATE_TIME_FORMATTER.parseUnresolved(time, new ParsePosition(offset));
        // Bail fast
        if (parsed == null) {
            return null;
        }

        int secondOfMinute = getFieldFromTemporalAccessor(parsed, ChronoField.SECOND_OF_MINUTE);
        final int hourOfDay = getFieldFromTemporalAccessor(parsed, ChronoField.HOUR_OF_DAY);
        final int minuteOfHour = getFieldFromTemporalAccessor(parsed, ChronoField.MINUTE_OF_HOUR);

        // Check whether the input time has leap second. As the leap second can only
        // occur at 23:59:60, we can be very strict, and don't interpret an incorrect
        // value as leap second.
        if (secondOfMinute != 60 || minuteOfHour != 59 || hourOfDay != 23) {
            return null;
        }

        LOG.trace("Received time contains leap second, adjusting by replacing the second-of-minute of 60 with 59 {}",
                time);

        // Applying simple conversion replacing the second-of-minute of 60 with 59.

        secondOfMinute = 59;

        final int year = getFieldFromTemporalAccessor(parsed, ChronoField.YEAR);
        final int monthOfYear = getFieldFromTemporalAccessor(parsed, ChronoField.MONTH_OF_YEAR);
        final int dayOfMonth = getFieldFromTemporalAccessor(parsed, ChronoField.DAY_OF_MONTH);
        final int nanoOfSecond = getFieldFromTemporalAccessor(parsed, ChronoField.NANO_OF_SECOND);
        final int offsetSeconds = getFieldFromTemporalAccessor(parsed, ChronoField.OFFSET_SECONDS);

        final var currentTime = LocalDateTime.of(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute,
            nanoOfSecond);
        final var dateTimeWithZoneOffset = currentTime.atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds));
        return RFC3339_DATE_PARSER.apply(dateTimeWithZoneOffset.toString());
    }

    /**
     * Get value associated with {@code ChronoField}.
     *
     * @param accessor The {@link TemporalAccessor}
     * @param field The {@link ChronoField} to get
     * @return the value associated with the {@link ChronoField} for the given {@link TemporalAccessor} if present,
     *     else 0.
     */
    private static int getFieldFromTemporalAccessor(final TemporalAccessor accessor, final ChronoField field) {
        return accessor.isSupported(field) ? (int) accessor.getLong(field) : 0;
    }

    private final @NonNull Instant eventTime;

    private NotificationMessage(final Document notificationContent, final Instant eventTime) {
        super(notificationContent);
        this.eventTime = requireNonNull(eventTime);
    }

    /**
     * Create new NotificationMessage with provided document. Only to be used if we know that the document represents a
     * valid NotificationMessage.
     */
    static @NonNull NotificationMessage ofChecked(final Document document) throws DocumentedException {
        final var eventTime = document.getDocumentElement().getElementsByTagNameNS(NamespaceURN.NOTIFICATION,
            XmlNetconfConstants.EVENT_TIME);
        if (eventTime.getLength() < 1) {
            throw new DocumentedException("Missing event-time", ErrorType.PROTOCOL, ErrorTag.MISSING_ELEMENT,
                ErrorSeverity.ERROR, ImmutableMap.of());
        }
        return new NotificationMessage(document, RFC3339_DATE_PARSER.apply(eventTime.item(0).getTextContent()));
    }

    /**
     * Create new notification with provided timestamp.
     */
    public static @NonNull NotificationMessage ofNotificationContent(final Document notificationContent,
            final Instant eventTime) {
        return new NotificationMessage(wrapNotification(notificationContent, eventTime), eventTime);
    }

    /**
     * Create new notification and capture the timestamp in the method call.
     */
    public static @NonNull NotificationMessage ofNotificationContent(final Document notificationContent) {
        return ofNotificationContent(notificationContent, Instant.now());
    }

    /**
     * Get the time of the event.
     *
     * @return notification event time
     */
    public @NonNull Instant getEventTime() {
        return eventTime;
    }

    private static Document wrapNotification(final Document notificationContent, final Instant eventTime) {
        requireNonNull(notificationContent);
        requireNonNull(eventTime);

        final var baseNotification = notificationContent.getDocumentElement();
        final var entireNotification = notificationContent.createElementNS(NamespaceURN.NOTIFICATION, ELEMENT_NAME);
        entireNotification.appendChild(baseNotification);

        final var eventTimeElement = notificationContent.createElementNS(NamespaceURN.NOTIFICATION,
            XmlNetconfConstants.EVENT_TIME);
        eventTimeElement.setTextContent(RFC3339_DATE_FORMATTER.apply(eventTime));
        entireNotification.appendChild(eventTimeElement);

        notificationContent.appendChild(entireNotification);
        return notificationContent;
    }
}
