/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

/**
 * Features of query parameters part of both notifications.
 */
abstract class AbstractQueryParams extends AbstractNotificationsData {
    // FIXME: these should be final
    private Instant startTime = null;
    private Instant stopTime = null;
    private boolean leafNodesOnly = false;
    private boolean skipNotificationData = false;

    @VisibleForTesting
    public final Instant getStart() {
        return startTime;
    }

    /**
     * Set query parameters for listener.
     *
     * @param startTime     Start-time of getting notification.
     * @param stopTime      Stop-time of getting notification.
     * @param filter        Indicates which subset of all possible events are of interest.
     * @param leafNodesOnly If TRUE, notifications will contain changes of leaf nodes only.
     */
    public abstract void setQueryParams(Instant startTime, Instant stopTime, String filter,
            boolean leafNodesOnly, boolean skipNotificationData);

    @SuppressWarnings("checkstyle:hiddenField")
    final void setQueryParams(final Instant startTime, final Instant stopTime, final boolean leafNodesOnly,
            final boolean skipNotificationData) {
        this.startTime = requireNonNull(startTime);
        this.stopTime = stopTime;
        this.leafNodesOnly = leafNodesOnly;
        this.skipNotificationData = skipNotificationData;
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
        if (stopTime != null) {
            if (startTime.compareTo(now) < 0 && stopTime.compareTo(now) > 0) {
                return true;
            }
            if (stopTime.compareTo(now) < 0) {
                try {
                    listener.close();
                } catch (final Exception e) {
                    throw new RestconfDocumentedException("Problem with unregister listener." + e);
                }
            }
        } else if (startTime != null) {
            if (startTime.compareTo(now) < 0) {
                startTime = null;
                return true;
            }
        } else {
            return true;
        }
        return false;
    }
}
