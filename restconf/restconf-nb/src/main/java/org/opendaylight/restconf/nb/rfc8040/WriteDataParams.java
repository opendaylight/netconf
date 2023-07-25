/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.PointParam;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Parser and holder of query parameters from uriInfo for data and datastore modification operations.
 */
// FIXME: this should be a record with JDK17+
public final class WriteDataParams implements Immutable {
    private static final @NonNull WriteDataParams EMPTY = new WriteDataParams(null, null);

    private final PointParam point;
    private final InsertParam insert;

    private WriteDataParams(final InsertParam insert, final PointParam point) {
        this.insert = insert;
        this.point = point;
    }

    public static @NonNull WriteDataParams empty() {
        return EMPTY;
    }

    public static @NonNull WriteDataParams of(final InsertParam insert, final PointParam point) {
        if (point == null) {
            if (insert == null) {
                return empty();
            }

            // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.5:
            //        If the values "before" or "after" are used, then a "point" query
            //        parameter for the "insert" query parameter MUST also be present, or a
            //        "400 Bad Request" status-line is returned.
            if (insert == InsertParam.BEFORE || insert == InsertParam.AFTER) {
                throw new IllegalArgumentException(
                    "Insert parameter " + insert.paramValue() + " cannot be used without a Point parameter.");
            }
        } else {
            // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.6:
            // [when "point" parameter is present and]
            //        If the "insert" query parameter is not present or has a value other
            //        than "before" or "after", then a "400 Bad Request" status-line is
            //        returned.
            if (insert != InsertParam.BEFORE && insert != InsertParam.AFTER) {
                throw new IllegalArgumentException(
                    "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
            }
        }

        return new WriteDataParams(insert, point);
    }

    public @Nullable InsertParam insert() {
        return insert;
    }

    public @Nullable PointParam point() {
        return point;
    }

    @Beta
    // FIXME: it seems callers' structure should be able to cater with just point() and insert()
    public @NonNull PointParam getPoint() {
        return verifyNotNull(point);
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(this).omitNullValues();
        if (insert != null) {
            helper.add("insert", insert.paramValue());
        }
        if (point != null) {
            helper.add("point", point.value());
        }
        return helper.toString();
    }
}