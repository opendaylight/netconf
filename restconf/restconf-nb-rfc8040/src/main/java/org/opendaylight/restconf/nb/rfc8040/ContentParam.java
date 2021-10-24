/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Enumeration of possible {@code content} values as defined by
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.1">RFC8040, section 4.8.1</a>.
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

    private final @NonNull String uriValue;

    ContentParam(final String uriValue) {
        this.uriValue = requireNonNull(uriValue);
    }

    @Override
    public final Class<@NonNull ContentParam> javaClass() {
        return ContentParam.class;
    }

    @Override
    public final String paramName() {
        return uriName();
    }

    public @NonNull String uriValue() {
        return uriValue;
    }

    public static @NonNull String uriName() {
        return "content";
    }

    // Note: returns null of unknowns
    public static @Nullable ContentParam forUriValue(final String uriValue) {
        switch (uriValue) {
            case "all":
                return ALL;
            case "config":
                return CONFIG;
            case "nonconfig":
                return NONCONFIG;
            default:
                return null;
        }
    }
}
