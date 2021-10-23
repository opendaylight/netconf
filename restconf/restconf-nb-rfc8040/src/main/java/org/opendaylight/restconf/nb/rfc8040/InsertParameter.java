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
 * Enumeration of possible {@code insert} values as defined by
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.1">RFC8040, section 4.8.1</a>.
 */
public enum InsertParameter {
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

    private @NonNull String uriValue;

    InsertParameter(final String uriValue) {
        this.uriValue = requireNonNull(uriValue);
    }

    public @NonNull String uriValue() {
        return uriValue;
    }

    public static @NonNull String uriName() {
        return "insert";
    }

    // Note: returns null of unknowns
    public static @Nullable InsertParameter forUriValue(final String uriValue) {
        switch (uriValue) {
            case "after":
                return AFTER;
            case "before":
                return BEFORE;
            case "first":
                return FIRST;
            case "last":
                return LAST;
            default:
                return null;
        }
    }
}