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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.RestconfQueryParam;

/**
 * Default implementation of {@link QueryParameters}.
 */
@NonNullByDefault
public record ImmutableQueryParameters(ImmutableMap<String, String> params) implements QueryParameters {
    // TODO: consider caching common request parameter combinations
    private static final ImmutableQueryParameters EMPTY = new ImmutableQueryParameters(ImmutableMap.of());

    public ImmutableQueryParameters {
        requireNonNull(params);
    }

    public static final ImmutableQueryParameters of() {
        return EMPTY;
    }

    public static ImmutableQueryParameters of(final String paramName, final String paramValue) {
        return new ImmutableQueryParameters(ImmutableMap.of(paramName, paramValue));
    }

    public static ImmutableQueryParameters of(final Entry<String, String> entry) {
        return of(entry.getKey(), entry.getValue());
    }

    public static ImmutableQueryParameters of(final RestconfQueryParam<?> param) {
        return of(param.paramName(), param.paramValue());
    }

    public static ImmutableQueryParameters of(final RestconfQueryParam<?>... params) {
        return switch (params.length) {
            case 0 -> of();
            case 1 -> of(params[0]);
            default -> newOf(Arrays.asList(params));
        };
    }

    public static ImmutableQueryParameters of(final Collection<RestconfQueryParam<?>> params) {
        return params instanceof List ? of((List<RestconfQueryParam<?>>) params) : switch (params.size()) {
            case 0 -> of();
            case 1 -> of(params.iterator().next());
            default -> newOf(params);
        };
    }

    public static ImmutableQueryParameters of(final List<RestconfQueryParam<?>> params) {
        return switch (params.size()) {
            case 0 -> of();
            case 1 -> of(params.get(0));
            default -> newOf(params);
        };
    }

    public static final ImmutableQueryParameters of(final Map<String, String> params) {
        return params.isEmpty() ? of() : new ImmutableQueryParameters(ImmutableMap.copyOf(params));
    }

    /**
     * Normalize query parameters from an map containing zero or more values for each parameter value, such as coming
     * from JAX-RS's {@code UriInfo}.
     *
     * @param multiParams Input map
     * @return An {@link ImmutableQueryParameters} instance
     * @throws NullPointerException if {@code uriInfo} is {@code null}
     * @throws IllegalArgumentException if there are multiple values for a parameter
     */
    public static final ImmutableQueryParameters ofMultiValue(final Map<String, List<String>> multiParams) {
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
        return ImmutableQueryParameters.of(builder.build());
    }

    private static ImmutableQueryParameters newOf(final Collection<RestconfQueryParam<?>> params) {
        return new ImmutableQueryParameters(params.stream()
            .collect(ImmutableMap.toImmutableMap(RestconfQueryParam::paramName, RestconfQueryParam::paramValue)));
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
    public <T extends RestconfQueryParam<T>> T getDefault(final Class<T> javaClass) {
        throw new NoSuchElementException("No default defined for " + javaClass.getCanonicalName());
    }

    @Override
    public String toString() {
        return QueryParameters.class.getSimpleName() + "(" + params + ")";
    }
}
