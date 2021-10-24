/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.util.List;
import java.util.Map.Entry;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
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

    public static @NonNull NotificationQueryParams fromUriInfo(final UriInfo uriInfo) {
        StartTimeParameter startTime = null;
        StopTimeParameter stopTime = null;
        FilterParameter filter = null;
        boolean skipNotificationData = false;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            final String paramName = entry.getKey();
            final List<String> paramValues = entry.getValue();
            if (paramName.equals(StartTimeParameter.uriName())) {
                switch (paramValues.size()) {
                    case 0:
                        break;
                    case 1:
                        final String str = paramValues.get(0);
                        try {
                            startTime = StartTimeParameter.forUriValue(str);
                        } catch (IllegalArgumentException e) {
                            throw new RestconfDocumentedException("Invalid start-time date: " + str, e);
                        }
                        break;
                    default:
                        throw new RestconfDocumentedException("Start-time parameter can be used only once.");
                }
            } else if (paramName.equals(StopTimeParameter.uriName())) {
                switch (paramValues.size()) {
                    case 0:
                        break;
                    case 1:
                        final String str = paramValues.get(0);
                        try {
                            stopTime = StopTimeParameter.forUriValue(str);
                        } catch (IllegalArgumentException e) {
                            throw new RestconfDocumentedException("Invalid stop-time date: " + str, e);
                        }
                        break;
                    default:
                        throw new RestconfDocumentedException("Stop-time parameter can be used only once.");
                }
            } else if (paramName.equals(FilterParameter.uriName())) {
                if (!paramValues.isEmpty()) {
                    filter = FilterParameter.forUriValue(paramValues.get(0));
                }
            } else if (paramName.equals("odl-skip-notification-data")) {
                switch (paramValues.size()) {
                    case 0:
                        break;
                    case 1:
                        skipNotificationData = Boolean.parseBoolean(paramValues.get(0));
                        break;
                    default:
                        throw new RestconfDocumentedException(
                            "Odl-skip-notification-data parameter can be used only once.");
                }
            } else {
                throw new RestconfDocumentedException("Bad parameter used with notifications: " + paramName);
            }
        }
        if (startTime == null && stopTime != null) {
            throw new RestconfDocumentedException("Stop-time parameter has to be used with start-time parameter.");
        }

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
}