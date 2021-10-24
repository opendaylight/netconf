/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Parser and holder of query paramteres from uriInfo for notifications.
 */
public final class NotificationQueryParams implements Immutable {
    private final StartTimeParameter startTime;
    private final StopTimeParameter stopTime;
    private final FilterParameter filter;
    private final boolean skipNotificationData;

    private NotificationQueryParams(final StartTimeParameter startTime, final StopTimeParameter stopTime,
            final FilterParameter filter, final boolean skipNotificationData) {
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.filter = filter;
        this.skipNotificationData = skipNotificationData;
    }

    public static @NonNull NotificationQueryParams of(final StartTimeParameter startTime,
            final StopTimeParameter stopTime, final FilterParameter filter, final boolean skipNotificationData) {
        checkArgument(stopTime == null || startTime != null,
            "Stop-time parameter has to be used with start-time parameter.");
        return new NotificationQueryParams(startTime, stopTime, filter, skipNotificationData);
    }

    /**
     * Get start-time query parameter.
     *
     * @return start-time
     */
    public @Nullable StartTimeParameter startTime() {
        return startTime;
    }

    /**
     * Get stop-time query parameter.
     *
     * @return stop-time
     */
    public @Nullable StopTimeParameter stopTime() {
        return stopTime;
    }

    /**
     * Get filter query parameter.
     *
     * @return filter
     */
    public @Nullable FilterParameter filter() {
        return filter;
    }

    /**
     * Check whether this query should notify changes without data.
     *
     * @return true if this query should notify about changes with  data
     */
    public boolean isSkipNotificationData() {
        return skipNotificationData;
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(this);
        if (startTime != null) {
            helper.add("startTime", startTime.uriValue());
        }
        if (stopTime != null) {
            helper.add("stopTime", stopTime.uriValue());
        }
        if (filter != null) {
            helper.add("filter", filter.uriValue());
        }
        return helper.add("skipNotificationData", skipNotificationData).toString();
    }
}
