/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications;

import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.text.ParsePosition;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.function.Function;
import org.opendaylight.netconf.api.NetconfMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Special kind of netconf message that contains a timestamp.
 */
public final class NetconfNotification extends NetconfMessage {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNotification.class);

    public static final String NOTIFICATION = "notification";
    public static final String NOTIFICATION_NAMESPACE = "urn:ietf:params:netconf:capability:notification:1.0";

    /**
     * The ISO-like date-time formatter that formats or parses a date-time with
     * the offset and zone if available, such as '2011-12-03T10:15:30',
     * '2011-12-03T10:15:30+01:00' or '2011-12-03T10:15:30+01:00[Europe/Paris]'.
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Provide a {@link String} representation of a {@link Date} object,
     * using the time-zone offset for UTC, {@code ZoneOffset.UTC}.
     */
    public static final Function<Date, String> RFC3339_DATE_FORMATTER = date ->
            DATE_TIME_FORMATTER.format(date.toInstant().atOffset(ZoneOffset.UTC));

    /**
     * Parse a {@link String} object into a {@link Date} using the time-zone
     * offset for UTC, {@code ZoneOffset.UTC}, and the system default time-zone,
     * {@code ZoneId.systemDefault()}.
     * <p>
     *     While parsing, if an exception occurs, we try to handle it as if it is due
     *     to a leap second. If that's the case, a simple conversion is applied, replacing
     *     the second-of-minute of 60 with 59.
     *     If that's not the case, we propagate the {@link DateTimeParseException} to the
     *     caller.
     * </p>
     */
    public static final Function<String, Date> RFC3339_DATE_PARSER = time -> {
        try {
            final ZonedDateTime localDateTime = ZonedDateTime.parse(time, DATE_TIME_FORMATTER);
            final int startAt = 0;
            final TemporalAccessor parsed = DATE_TIME_FORMATTER.parse(time, new ParsePosition(startAt));
            final int nanoOfSecond = getFieldFromTemporalAccessor(parsed, ChronoField.NANO_OF_SECOND);
            final long reminder = nanoOfSecond % 1000000;

            // Log warn in case we rounded the fraction of a second. We need to create a string from the
            // value that was cut. Example -> 1.123750 -> Value that was cut 75
            if (reminder != 0) {
                final StringBuilder reminderBuilder = new StringBuilder(String.valueOf(reminder));

                //add zeros in case we have number like 123056 to make sure 056 is displayed
                while (reminderBuilder.length() < 6) {
                    reminderBuilder.insert(0, '0');
                }

                //delete zeros from end to make sure that number like 1.123750 will show value cut 75.
                while (reminderBuilder.charAt(reminderBuilder.length() - 1) == '0') {
                    reminderBuilder.deleteCharAt(reminderBuilder.length() - 1);
                }
                LOG.warn("Fraction of second is cut to three digits. Value that was cut {}",
                        reminderBuilder.toString());
            }

            return Date.from(Instant.from(localDateTime));
        } catch (DateTimeParseException exception) {
            Date res = handlePotentialLeapSecond(time);
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
     *     @return {@code null} if time isn't ISO compliant or if the time doesn't have a leap second
     *     else a {@link Date} as per as the RFC3339_DATE_PARSER.
     */
    private static Date handlePotentialLeapSecond(final String time) {
        // Parse the string from offset 0, so we get the whole value.
        final int offset = 0;
        final TemporalAccessor parsed = DATE_TIME_FORMATTER.parseUnresolved(time, new ParsePosition(offset));
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

        final LocalDateTime currentTime = LocalDateTime.of(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour,
                secondOfMinute, nanoOfSecond);
        final OffsetDateTime dateTimeWithZoneOffset = currentTime.atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds));

        return RFC3339_DATE_PARSER.apply(dateTimeWithZoneOffset.toString());
    }

    /**
     * Get value asociated with {@code ChronoField}.
     *
     * @param accessor The {@link TemporalAccessor}
     * @param field The {@link ChronoField} to get
     * @return the value associated with the {@link ChronoField} for the given {@link TemporalAccessor} if present,
     *     else 0.
     */
    private static int getFieldFromTemporalAccessor(final TemporalAccessor accessor, final ChronoField field) {
        return accessor.isSupported(field) ? (int) accessor.getLong(field) : 0;
    }

    public static final String EVENT_TIME = "eventTime";

    /**
     * Used for unknown/un-parse-able event-times.
     */
    public static final Date UNKNOWN_EVENT_TIME = new Date(0);

    private final Date eventTime;

    /**
     * Create new notification and capture the timestamp in the constructor.
     */
    public NetconfNotification(final Document notificationContent) {
        this(notificationContent, new Date());
    }

    /**
     * Create new notification with provided timestamp.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2") // stores a reference to an externally mutable Date object
    public NetconfNotification(final Document notificationContent, final Date eventTime) {
        super(wrapNotification(notificationContent, eventTime));
        this.eventTime = eventTime;
    }

    /**
     * Get the time of the event.
     *
     * @return notification event time
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public Date getEventTime() {
        return eventTime;
    }

    private static Document wrapNotification(final Document notificationContent, final Date eventTime) {
        Preconditions.checkNotNull(notificationContent);
        Preconditions.checkNotNull(eventTime);

        final Element baseNotification = notificationContent.getDocumentElement();
        final Element entireNotification = notificationContent.createElementNS(NOTIFICATION_NAMESPACE, NOTIFICATION);
        entireNotification.appendChild(baseNotification);

        final Element eventTimeElement = notificationContent.createElementNS(NOTIFICATION_NAMESPACE, EVENT_TIME);
        eventTimeElement.setTextContent(getSerializedEventTime(eventTime));
        entireNotification.appendChild(eventTimeElement);

        notificationContent.appendChild(entireNotification);
        return notificationContent;
    }

    private static String getSerializedEventTime(final Date eventTime) {
        return RFC3339_DATE_FORMATTER.apply(eventTime);
    }
}
