/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.PointParam;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Parser and holder of query parameters from uriInfo for data and datastore modification operations.
 */
// FIXME: Java 21: model this as a sealed interface and two records BeforeOrAfter and FirstOrLast, which further expose
//                 a boolean to differentiate which of the cases we are dealing with. This will allow users to use
//                 switch expression with record decomposition to safely dispatch execution. Only BeforeOrAfter will
//                 have a @NonNull PointParam then and there will not be an insert field. We can also ditch toString(),
//                 as the records will do the right thing.
public final class Insert implements Immutable {
    private final @NonNull InsertParam insert;
    private final @Nullable PointParam point;

    private Insert(final InsertParam insert, final PointParam point) {
        this.insert = requireNonNull(insert);
        this.point = point;
    }

    public static @Nullable Insert forParams(final @Nullable InsertParam insert, final @Nullable PointParam point) {
        if (insert == null) {
            if (point != null) {
                throw invalidPointIAE();
            }
            return null;
        }

        return switch (insert) {
            case BEFORE, AFTER -> {
                // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.5:
                //        If the values "before" or "after" are used, then a "point" query
                //        parameter for the "insert" query parameter MUST also be present, or a
                //        "400 Bad Request" status-line is returned.
                if (point == null) {
                    throw new IllegalArgumentException(
                        "Insert parameter " + insert.paramValue() + " cannot be used without a Point parameter.");
                }
                yield new Insert(insert, point);
            }
            case FIRST, LAST -> {
                // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.6:
                // [when "point" parameter is present and]
                //        If the "insert" query parameter is not present or has a value other
                //        than "before" or "after", then a "400 Bad Request" status-line is
                //        returned.
                if (point != null) {
                    throw invalidPointIAE();
                }
                yield new Insert(insert, null);
            }
        };
    }

    private static IllegalArgumentException invalidPointIAE() {
        return new IllegalArgumentException(
            "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
    }

    public @NonNull InsertParam insert() {
        return insert;
    }

    public @Nullable PointParam point() {
        return point;
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(this).add("insert", insert.paramValue());
        final var local = point;
        if (local != null) {
            helper.add("point", local.value());
        }
        return helper.toString();
    }
}