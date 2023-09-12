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
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.PointParam;

/**
 * Parser and holder of query parameters from uriInfo for data and datastore modification operations.
 */
public sealed interface Insert {
    record FirstOrLast(boolean isFirst) implements Insert {
        FirstOrLast(final @Nullable PointParam point, final boolean isFirst) {
            this(isFirst);
            checkNull(point);
        }
    }

    record BeforeOrAfter(boolean isBefore, @NonNull PointParam point) implements Insert {
        public BeforeOrAfter {
            requireNonNull(point);
        }

        BeforeOrAfter(final @Nullable PointParam point, final boolean isBefore) {
            this(isBefore, checkNotNull(point));
        }
    }

    static @Nullable Insert forParams(final @Nullable InsertParam insert, final @Nullable PointParam point) {
        if (insert == null) {
            checkNull(point);
            return null;
        }

        return switch (insert) {
            case BEFORE -> new BeforeOrAfter(point, true);
            case AFTER -> new BeforeOrAfter(point, false);
            case FIRST -> new FirstOrLast(point, true);
            case LAST -> new FirstOrLast(point, false);
        };
    }

    // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.6:
    // [when "point" parameter is present and]
    //        If the "insert" query parameter is not present or has a value other
    //        than "before" or "after", then a "400 Bad Request" status-line is
    //        returned.
    private static void checkNull(final @Nullable PointParam point) {
        if (point != null) {
            throw new IllegalArgumentException(
                "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
        }
    }

    // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.5:
    //        If the values "before" or "after" are used, then a "point" query
    //        parameter for the "insert" query parameter MUST also be present, or a
    //        "400 Bad Request" status-line is returned.
    private static @NonNull PointParam checkNotNull(final @Nullable PointParam point) {
        if (point == null) {
            throw new IllegalArgumentException(
                "Insert parameter 'after' or 'before' can only be used with a Point parameter.");
        }
        return point;
    }
}