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
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.RestconfQueryParam;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Query parameters of a RESTCONF request URI. Individual parameters can be looked up by
 * {@link RestconfQueryParam#paramName()} via {@link #lookup(String)}. All parameters are accessible via
 * {@link #asCollection()}, where each {@link RestconfQueryParam#paramName()} is guaranteed to be encountered at most
 * once.
 */
@NonNullByDefault
public final class QueryParameters implements Immutable {
    static final QueryParameters EMPTY = new QueryParameters(ImmutableMap.of());

    private final ImmutableMap<String, String> params;

    private QueryParameters(final ImmutableMap<String, String> params) {
        this.params = requireNonNull(params);
    }

    private QueryParameters(final Collection<RestconfQueryParam<?>> params) {
        // TODO: consider caching common request parameter combinations
        this(params.stream()
            .collect(ImmutableMap.toImmutableMap(RestconfQueryParam::paramName, RestconfQueryParam::paramValue)));
    }

    public static QueryParameters of() {
        return EMPTY;
    }

    public static QueryParameters of(final String paramName, final String paramValue) {
        return new QueryParameters(ImmutableMap.of(paramName, paramValue));
    }

    public static QueryParameters of(final Entry<String, String> entry) {
        return of(entry.getKey(), entry.getValue());
    }

    public static QueryParameters of(final RestconfQueryParam<?> param) {
        return of(param.paramName(), param.paramValue());
    }

    public static QueryParameters of(final RestconfQueryParam<?>... params) {
        return switch (params.length) {
            case 0 -> of();
            case 1 -> of(params[0]);
            default -> new QueryParameters(Arrays.asList(params));
        };
    }

    public static QueryParameters of(final Collection<RestconfQueryParam<?>> params) {
        return params instanceof List ? of((List<RestconfQueryParam<?>>) params) : switch (params.size()) {
            case 0 -> of();
            case 1 -> of(params.iterator().next());
            default -> new QueryParameters(params);
        };
    }

    public static QueryParameters of(final List<RestconfQueryParam<?>> params) {
        return switch (params.size()) {
            case 0 -> of();
            case 1 -> of(params.get(0));
            default -> new QueryParameters(params);
        };
    }

    public static QueryParameters of(final Map<String, String> params) {
        return params.isEmpty() ? of() : new QueryParameters(ImmutableMap.copyOf(params));
    }

    /**
     * Normalize query parameters from an map containing zero or more values for each parameter value, such as coming
     * from JAX-RS's {@code UriInfo}.
     *
     * @param multiParams Input map
     * @return A {@link QueryParameters} instance
     * @throws NullPointerException if {@code uriInfo} is {@code null}
     * @throws IllegalArgumentException if there are multiple values for a parameter
     */
    public static QueryParameters ofMultiValue(final Map<String, List<String>> multiParams) {
        if (multiParams.isEmpty()) {
            return of();
        }

        final var builder = ImmutableMap.<String, String>builder();
        for (var entry : multiParams.entrySet()) {
            final var values = entry.getValue();
            switch (values.size()) {
                case 0 -> {
                    // No-op
                }
                case 1 -> builder.put(entry.getKey(), values.get(0));
                default -> throw new IllegalArgumentException(
                    "Parameter " + entry.getKey() + " can appear at most once in request URI");
            }
        }

        final var params = builder.build();
        return params.isEmpty() ? of() : new QueryParameters(params);
    }

    public boolean isEmpty() {
        return params.isEmpty();
    }

    public Collection<Entry<String, String>> asCollection() {
        return params.entrySet();
    }

    public @Nullable String lookup(final String paramName) {
        return params.get(requireNonNull(paramName));
    }

    public <T> @Nullable T lookup(final String paramName, final Function<String, T> parseFunction) {
        final var str = lookup(paramName);
        if (str != null) {
            return parseFunction.apply(str);
        }
        return null;
    }

    public QueryParameters withoutParam(final String paramName) {
        return params.containsKey(paramName) ? of(Maps.filterKeys(params, key -> !key.equals(paramName))) : this;
    }

    @Override
    public String toString() {
        return QueryParameters.class.getSimpleName() + "(" + params + ")";
    }

}
