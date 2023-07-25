/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Interface implemented by all Java classes which represent a
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8">RESTCONF query parameter</a>.
 *
 * <p>
 * Implementations of this interface are required to expose a {@code public static @NonNull uriName} constant, which
 * holds the well-known URI Request Query Parameter name of the associated definition.
 *
 * <p>
 * This naming violates the usual Java coding style, we need it to keep API consistency as an enum can be used as an
 * implementation, in which case users could be confused by upper-case constants which are not enum members.
 */
public sealed interface RestconfQueryParam<T extends RestconfQueryParam<T>> extends Immutable
        permits ContentParam, DepthParam, FieldsParam, FilterParam, InsertParam, PointParam, WithDefaultsParam,
                AbstractReplayParam,
                ChangedLeafNodesOnlyParam, LeafNodesOnlyParam, PrettyPrintParam, SkipNotificationDataParam {
    /**
     * Return the Java representation class.
     *
     * @return the Java representation class
     */
    @NonNull Class<@NonNull T> javaClass();

    /**
     * Return the URI Request parameter name.
     *
     * @return the URI Request parameter name.
     */
    @NonNull String paramName();

    /**
     * Return the URI Request parameter value.
     *
     * @return the URI Request parameter value.
     */
    @NonNull String paramValue();
}
