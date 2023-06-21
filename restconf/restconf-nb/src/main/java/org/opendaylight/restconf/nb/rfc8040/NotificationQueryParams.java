/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.ChangedLeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.ChildNodesOnlyParam;
import org.opendaylight.restconf.api.query.FilterParam;
import org.opendaylight.restconf.api.query.LeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.SkipNotificationDataParam;
import org.opendaylight.restconf.api.query.StartTimeParam;
import org.opendaylight.restconf.api.query.StopTimeParam;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Parser and holder of query parameters from uriInfo for notifications.
 */
public final class NotificationQueryParams implements Immutable {
    private final SkipNotificationDataParam skipNotificationData;
    private final LeafNodesOnlyParam leafNodesOnly;
    private final StartTimeParam startTime;
    private final StopTimeParam stopTime;
    private final FilterParam filter;
    private final ChangedLeafNodesOnlyParam changedLeafNodesOnly;
    private final ChildNodesOnlyParam childNodesOnly;

    private NotificationQueryParams(final StartTimeParam startTime, final StopTimeParam stopTime,
            final FilterParam filter, final LeafNodesOnlyParam leafNodesOnly,
            final SkipNotificationDataParam skipNotificationData, final ChangedLeafNodesOnlyParam changedLeafNodesOnly,
            final ChildNodesOnlyParam childNodesOnly) {
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.filter = filter;
        this.leafNodesOnly = leafNodesOnly;
        this.skipNotificationData = skipNotificationData;
        this.changedLeafNodesOnly = changedLeafNodesOnly;
        this.childNodesOnly = childNodesOnly;
    }

    public static @NonNull NotificationQueryParams of(final StartTimeParam startTime, final StopTimeParam stopTime,
            final FilterParam filter, final LeafNodesOnlyParam leafNodesOnly,
            final SkipNotificationDataParam skipNotificationData, final ChangedLeafNodesOnlyParam changedLeafNodesOnly,
            final ChildNodesOnlyParam childNodesOnly) {
        if (stopTime != null && startTime == null) {
            throw new IllegalArgumentException(StopTimeParam.uriName + " parameter has to be used with "
                + StartTimeParam.uriName + " parameter");
        }
        if (changedLeafNodesOnly != null) {
            if (leafNodesOnly != null) {
                throw new IllegalArgumentException(ChangedLeafNodesOnlyParam.uriName + " parameter cannot be used with "
                    + LeafNodesOnlyParam.uriName + " parameter");
            }
            if (childNodesOnly != null) {
                throw new IllegalArgumentException(ChangedLeafNodesOnlyParam.uriName + " parameter cannot be used with "
                    + ChildNodesOnlyParam.uriName + " parameter");
            }
        }
        return new NotificationQueryParams(startTime, stopTime, filter, leafNodesOnly, skipNotificationData,
            changedLeafNodesOnly, childNodesOnly);
    }

    /**
     * Get start-time query parameter.
     *
     * @return start-time
     */
    public @Nullable StartTimeParam startTime() {
        return startTime;
    }

    /**
     * Get stop-time query parameter.
     *
     * @return stop-time
     */
    public @Nullable StopTimeParam stopTime() {
        return stopTime;
    }

    /**
     * Get filter query parameter.
     *
     * @return filter
     */
    public @Nullable FilterParam filter() {
        return filter;
    }

    /**
     * Get odl-leaf-nodes-only query parameter.
     *
     * @return odl-leaf-nodes-only
     */
    public @Nullable LeafNodesOnlyParam leafNodesOnly() {
        return leafNodesOnly;
    }

    /**
     * Get odl-skip-notification-data query parameter.
     *
     * @return odl-skip-notification-data
     */
    public @Nullable SkipNotificationDataParam skipNotificationData() {
        return skipNotificationData;
    }

    /**
     * Get changed-leaf-nodes-only query parameter.
     *
     * @return changed-leaf-nodes-only
     */
    public @Nullable ChangedLeafNodesOnlyParam changedLeafNodesOnly() {
        return changedLeafNodesOnly;
    }

    /**
     * Get odl-child-nodes-only query parameter.
     *
     * @return odl-child-nodes-only
     */
    public @Nullable ChildNodesOnlyParam childNodesOnly() {
        return childNodesOnly;
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(this);
        if (startTime != null) {
            helper.add("startTime", startTime.paramValue());
        }
        if (stopTime != null) {
            helper.add("stopTime", stopTime.paramValue());
        }
        if (filter != null) {
            helper.add("filter", filter.paramValue());
        }
        if (leafNodesOnly != null) {
            helper.add("leafNodesOnly", leafNodesOnly.value());
        }
        if (skipNotificationData != null) {
            helper.add("skipNotificationData", skipNotificationData.value());
        }
        if (changedLeafNodesOnly != null) {
            helper.add("changedLeafNodesOnly", changedLeafNodesOnly.value());
        }
        if (childNodesOnly != null) {
            helper.add("childNodesOnly", childNodesOnly.value());
        }
        return helper.toString();
    }
}
