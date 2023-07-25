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
 * Enumeration of possible {@code content} values as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.1">RFC8040, section 4.8.1</a>.
 */
public enum ContentParam implements RestconfQueryParam<ContentParam> {
    /**
     * Return all descendant data nodes.
     */
    ALL("all"),
    /**
     * Return only configuration descendant data nodes.
     */
    CONFIG("config"),
    /**
     * Return only non-configuration descendant data nodes.
     */
    NONCONFIG("nonconfig");

    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final @NonNull String uriName = "content";

    private final @NonNull String uriValue;

    ContentParam(final String uriValue) {
        this.uriValue = requireNonNull(uriValue);
    }

    @Override
    public final Class<ContentParam> javaClass() {
        return ContentParam.class;
    }

    @Override
    public final String paramName() {
        return uriName;
    }

    @Override
    public String paramValue() {
        return uriValue;
    }

    public static @NonNull ContentParam forUriValue(final String uriValue) {
        return switch (uriValue) {
            case "all" -> ALL;
            case "config" -> CONFIG;
            case "nonconfig" -> NONCONFIG;
            default -> throw new IllegalArgumentException(
                "Value can be 'all', 'config' or 'nonconfig', not '" + uriValue + "'");
        };
    }
}
