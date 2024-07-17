/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.QueryParameters;
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
public record EventStreamGetParams(
        StartTimeParam startTime,
        StopTimeParam stopTime,
        FilterParam filter,
        LeafNodesOnlyParam leafNodesOnly,
        SkipNotificationDataParam skipNotificationData,
        ChangedLeafNodesOnlyParam changedLeafNodesOnly,
        ChildNodesOnlyParam childNodesOnly) {
    public EventStreamGetParams {
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
     * Return {@link EventStreamGetParams} for specified query parameters.
     *
     * @param parames Parameters and their values
     * @return A {@link EventStreamGetParams}
     * @throws NullPointerException if {@code queryParameters} is {@code null}
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public static @NonNull EventStreamGetParams of(final QueryParameters parames) {
        StartTimeParam startTime = null;
        StopTimeParam stopTime = null;
        FilterParam filter = null;
        LeafNodesOnlyParam leafNodesOnly = null;
        SkipNotificationDataParam skipNotificationData = null;
        ChangedLeafNodesOnlyParam changedLeafNodesOnly = null;
        ChildNodesOnlyParam childNodesOnly = null;

        for (var entry : parames.asCollection()) {
            final var paramName = entry.getKey();
            final var paramValue = entry.getValue();

            switch (paramName) {
                case FilterParam.uriName:
                    filter = mandatoryParam(FilterParam::forUriValue, paramName, paramValue);
                    break;
                case StartTimeParam.uriName:
                    startTime = mandatoryParam(StartTimeParam::forUriValue, paramName, paramValue);
                    break;
                case StopTimeParam.uriName:
                    stopTime = mandatoryParam(StopTimeParam::forUriValue, paramName, paramValue);
                    break;
                case LeafNodesOnlyParam.uriName:
                    leafNodesOnly = mandatoryParam(LeafNodesOnlyParam::forUriValue, paramName, paramValue);
                    break;
                case SkipNotificationDataParam.uriName:
                    skipNotificationData = mandatoryParam(SkipNotificationDataParam::forUriValue, paramName,
                        paramValue);
                    break;
                case ChangedLeafNodesOnlyParam.uriName:
                    changedLeafNodesOnly = mandatoryParam(ChangedLeafNodesOnlyParam::forUriValue, paramName,
                        paramValue);
                    break;
                case ChildNodesOnlyParam.uriName:
                    childNodesOnly = mandatoryParam(ChildNodesOnlyParam::forUriValue, paramName, paramValue);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid parameter: " + paramName);
            }
        }

        return new EventStreamGetParams(startTime, stopTime, filter, leafNodesOnly, skipNotificationData,
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

    // FIXME: find a better place for this method
    public static <T> @NonNull T mandatoryParam(final Function<String, @NonNull T> factory, final String name,
            final String value) {
        try {
            return factory.apply(requireNonNull(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + name + " value: " + value, e);
        }
    }
}
