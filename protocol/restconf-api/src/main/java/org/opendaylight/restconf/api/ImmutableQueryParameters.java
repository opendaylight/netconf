/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.RestconfQueryParam;

/**
 * Default implementation of {@link QueryParameters}.
 */
@NonNullByDefault
record ImmutableQueryParameters(ImmutableMap<String, String> params) implements QueryParameters {
    static final ImmutableQueryParameters EMPTY = new ImmutableQueryParameters(ImmutableMap.of());

    ImmutableQueryParameters {
        requireNonNull(params);
    }

    ImmutableQueryParameters(final Collection<RestconfQueryParam<?>> params) {
        // TODO: consider caching common request parameter combinations
        this(params.stream()
            .collect(ImmutableMap.toImmutableMap(RestconfQueryParam::paramName, RestconfQueryParam::paramValue)));
    }

    @Override
    public boolean isEmpty() {
        return params.isEmpty();
    }

    @Override
    public Collection<Entry<String, String>> asCollection() {
        return params.entrySet();
    }

    @Override
    public @Nullable String lookup(final String paramName) {
        return params.get(requireNonNull(paramName));
    }

    @Override
    public String toString() {
        return QueryParameters.class.getSimpleName() + "(" + params + ")";
    }
}
