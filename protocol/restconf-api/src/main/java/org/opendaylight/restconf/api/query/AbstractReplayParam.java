/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;

/**
 * Abstract base class for StartTimeParameter and StopTimeParameter.
 */
public abstract sealed class AbstractReplayParam<T extends AbstractReplayParam<T>> implements RestconfQueryParam<T>
        permits StartTimeParam, StopTimeParam {
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4).appendLiteral('-')
        .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
        .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('T')
        .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendOffset("+HH:MM", "Z")
        .toFormatter();

    private final @NonNull DateAndTime value;

    AbstractReplayParam(final DateAndTime value) {
        this.value = requireNonNull(value);
    }

    public final @NonNull DateAndTime value() {
        return value;
    }

    @Override
    public final String paramValue() {
        return value.getValue();
    }

    /**
     * Return {@link #paramValue()} as an {@link Instant}. Note this method involves parsing the value string, which
     * is expensive and may fail. Callers should hold on to the returned value.
     *
     * @return An {@link Instant} instant corresponding to to {@link #paramValue()}
     * @throws DateTimeParseException if the value cannot be parsed or intepreted as an Instant
     */
    public final @NonNull Instant paramValueInstant() {
        return Instant.from(FORMATTER.parse(paramValue()));
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).add("value", paramValue()).toString();
    }
}
