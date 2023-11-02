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
import org.opendaylight.restconf.api.query.ChangedLeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.ChildNodesOnlyParam;
import org.opendaylight.restconf.api.query.FilterParam;
import org.opendaylight.restconf.api.query.LeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.SkipNotificationDataParam;
import org.opendaylight.restconf.api.query.StartTimeParam;
import org.opendaylight.restconf.api.query.StopTimeParam;

/**
 * Query parameters valid in the scope of a GET request on an event stream resource, as outline in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.3">RFC8040 section 6.3</a>.
 */
public record ReceiveEventsParams(
        StartTimeParam startTime,
        StopTimeParam stopTime,
        FilterParam filter,
        LeafNodesOnlyParam leafNodesOnly,
        SkipNotificationDataParam skipNotificationData,
        ChangedLeafNodesOnlyParam changedLeafNodesOnly,
        ChildNodesOnlyParam childNodesOnly) {
    public ReceiveEventsParams {
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
