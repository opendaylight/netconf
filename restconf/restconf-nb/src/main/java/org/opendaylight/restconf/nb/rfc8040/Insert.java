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
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Logical insert parameters.
 */
public sealed interface Insert extends Immutable {
    sealed interface WithPoint extends Insert {
        /**
         * Return the insertion point.
         *
         * @return A {@link PointParam}
         */
        @NonNull PointParam point();
    }

    record Before(@NonNull PointParam point) implements WithPoint {
        public Before {
            requireNonNull(point);
        }
    }

    record After(@NonNull PointParam point) implements WithPoint {
        public After {
            requireNonNull(point);
        }
    }

    record First() implements Insert {
        // Empty on purpose
    }

    record Last() implements Insert {
        // Empty on purpose
    }

    static @Nullable Insert of(final InsertParam insert, final PointParam point) {
        if (point == null) {
            if (insert == null) {
                return null;
            }

            // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.5:
            //        If the values "before" or "after" are used, then a "point" query
            //        parameter for the "insert" query parameter MUST also be present, or a
            //        "400 Bad Request" status-line is returned.
            return switch (insert) {
                case FIRST -> new First();
                case LAST -> new Last();
                default -> throw new IllegalArgumentException(
                    "Insert parameter " + insert.paramValue() + " cannot be used without a Point parameter.");

            };
        }

        // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.6:
        // [when "point" parameter is present and]
        //        If the "insert" query parameter is not present or has a value other
        //        than "before" or "after", then a "400 Bad Request" status-line is
        //        returned.
        return switch (insert) {
            case BEFORE -> new Before(point);
            case AFTER -> new After(point);
            default -> throw new IllegalArgumentException(
                "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
        };
    }
}