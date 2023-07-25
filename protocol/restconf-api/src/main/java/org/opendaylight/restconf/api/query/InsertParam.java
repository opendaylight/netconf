/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Enumeration of possible {@code insert} values as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.5">RFC8040, section 4.8.5</a>.
 */
public enum InsertParam implements RestconfQueryParam<InsertParam> {
    /**
     * Insert the new data after the insertion point, as specified by the value of the "point" parameter.
     */
    AFTER("after"),
    /**
     * Insert the new data before the insertion point, as specified by the value of the "point" parameter.
     */
    BEFORE("before"),
    /**
     * Insert the new data as the new first entry.
     */
    FIRST("first"),
    /**
     * Insert the new data as the new last entry.
     */
    LAST("last");

    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final @NonNull String uriName = "insert";

    private @NonNull String uriValue;

    InsertParam(final String uriValue) {
        this.uriValue = requireNonNull(uriValue);
    }

    @Override
    public Class<InsertParam> javaClass() {
        return InsertParam.class;
    }

    @Override
    public String paramName() {
        return uriName;
    }

    @Override
    public String paramValue() {
        return uriValue;
    }

    public static @NonNull InsertParam forUriValue(final String uriValue) {
        return switch (uriValue) {
            case "after" -> AFTER;
            case "before" -> BEFORE;
            case "first" -> FIRST;
            case "last" -> LAST;
            default -> throw new IllegalArgumentException(
                "Value can be 'after', 'before', 'first' or 'last', not '" + uriValue + "'");
        };
    }
}