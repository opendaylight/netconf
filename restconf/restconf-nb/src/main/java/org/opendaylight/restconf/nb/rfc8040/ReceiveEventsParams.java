/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
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

    /**
     * Return {@link ReceiveEventsParams} for specified query parameters.
     * @param queryParameters Parameters and their values
     * @return A {@link ReceiveEventsParams}
     */
    public static @NonNull ReceiveEventsParams ofQueryParameters(final Map<String, String> queryParameters) {
        StartTimeParam startTime = null;
        StopTimeParam stopTime = null;
        FilterParam filter = null;
        LeafNodesOnlyParam leafNodesOnly = null;
        SkipNotificationDataParam skipNotificationData = null;
        ChangedLeafNodesOnlyParam changedLeafNodesOnly = null;
        ChildNodesOnlyParam childNodesOnly = null;

        for (var entry : queryParameters.entrySet()) {
            final var paramName = entry.getKey();
            final var paramValue = entry.getValue();

            switch (paramName) {
                case FilterParam.uriName:
                    filter = optionalParam(FilterParam::forUriValue, paramName, paramValue);
                    break;
                case StartTimeParam.uriName:
                    startTime = optionalParam(StartTimeParam::forUriValue, paramName, paramValue);
                    break;
                case StopTimeParam.uriName:
                    stopTime = optionalParam(StopTimeParam::forUriValue, paramName, paramValue);
                    break;
                case LeafNodesOnlyParam.uriName:
                    leafNodesOnly = optionalParam(LeafNodesOnlyParam::forUriValue, paramName, paramValue);
                    break;
                case SkipNotificationDataParam.uriName:
                    skipNotificationData = optionalParam(SkipNotificationDataParam::forUriValue, paramName,
                        paramValue);
                    break;
                case ChangedLeafNodesOnlyParam.uriName:
                    changedLeafNodesOnly = optionalParam(ChangedLeafNodesOnlyParam::forUriValue, paramName,
                        paramValue);
                    break;
                case ChildNodesOnlyParam.uriName:
                    childNodesOnly = optionalParam(ChildNodesOnlyParam::forUriValue, paramName, paramValue);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid parameter: " + paramName);
            }
        }

        return new ReceiveEventsParams(startTime, stopTime, filter, leafNodesOnly, skipNotificationData,
            changedLeafNodesOnly, childNodesOnly);
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

    private static <T> @Nullable T optionalParam(final Function<String, @NonNull T> factory, final String name,
            final String value) {
        try {
            return factory.apply(requireNonNull(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + name + " value: " + value, e);
        }
    }
}
