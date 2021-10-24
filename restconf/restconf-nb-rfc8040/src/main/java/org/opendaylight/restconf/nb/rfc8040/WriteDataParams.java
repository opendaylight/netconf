/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Parser and holder of query parameters from uriInfo for data and datastore modification operations.
 */
// FIXME: this should be a record with JDK17+
public final class WriteDataParams implements Immutable {
    private static final @NonNull WriteDataParams EMPTY = new WriteDataParams(null, null);

    private final PointParameter point;
    private final InsertParameter insert;

    private WriteDataParams(final InsertParameter insert, final PointParameter point) {
        this.insert = insert;
        this.point = point;
    }

    public static @NonNull WriteDataParams empty() {
        return EMPTY;
    }

    public static @NonNull WriteDataParams of(final InsertParameter insert, final PointParameter point) {
        if (point == null) {
            if (insert == null) {
                return empty();
            }

            // https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.5:
            //        If the values "before" or "after" are used, then a "point" query
            //        parameter for the "insert" query parameter MUST also be present, or a
            //        "400 Bad Request" status-line is returned.
            if (insert == InsertParameter.BEFORE || insert == InsertParameter.AFTER) {
                throw new IllegalArgumentException(
                    "Insert parameter " + insert.uriValue() + " cannot be used without a Point parameter.");
            }
        } else {
            // https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.6:
            // [when "point" parameter is present and]
            //        If the "insert" query parameter is not present or has a value other
            //        than "before" or "after", then a "400 Bad Request" status-line is
            //        returned.
            if (insert != InsertParameter.BEFORE && insert != InsertParameter.AFTER) {
                throw new IllegalArgumentException(
                    "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
            }
        }

        return new WriteDataParams(insert, point);
    }

    public @Nullable InsertParameter insert() {
        return insert;
    }

    public @Nullable PointParameter point() {
        return point;
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(this).omitNullValues();
        if (insert != null) {
            helper.add("insert", insert.uriValue());
        }
        if (point != null) {
            helper.add("point", point.value());
        }
        return helper.toString();
    }
}