/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

import java.util.Arrays;
import java.util.Locale;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;

/**
 * Query parameters that can be used within POST/PUT HTTP methods.
 */
public class PutPostParameters {
    private final InsertParameter insertType;
    private final String insertPoint;

    private PutPostParameters(final InsertParameter insertType, final String insertPoint) {
        this.insertType = insertType;
        this.insertPoint = insertPoint;
    }

    public InsertParameter getInsertType() {
        return insertType;
    }

    public String getInsertPoint() {
        return insertPoint;
    }

    public static class PutPostParametersBuilder {
        private String insert;
        private String point;

        /**
         * The "insert" query parameter is used to specify how a resource should be inserted within
         * an "ordered-by user" list. Allowed parameter values: 'first', 'last', 'before', and 'after'.
         *
         * @param insert Value of the insert parameter.
         * @return Updated builder.
         */
        public PutPostParametersBuilder setInsert(final String insert) {
            this.insert = insert;
            return this;
        }

        /**
         * The "point" query parameter is used to specify the insertion point for a data resource that is
         * being created or moved within an "ordered-by user" list or leaf-list. The value of the "point" parameter
         * is a string that identifies the path to the insertion point object.
         *
         * @param point Value of the point parameter.
         * @return Updated builder.
         */
        public PutPostParametersBuilder setPoint(final String point) {
            this.point = point;
            return this;
        }

        public PutPostParameters build() {
            final InsertParameter insertType;
            if (insert != null) {
                try {
                    insertType = InsertParameter.valueOf(insert.toUpperCase(Locale.getDefault()));
                } catch (IllegalArgumentException e) {
                    throw new RestconfDocumentedException("The input insert parameter has invalid value, the allowed"
                            + " values are: " + Arrays.toString(InsertParameter.class.getEnumConstants()),
                            ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ATTRIBUTE, e);
                }
            } else {
                insertType = InsertParameter.LAST;
            }

            checkQueryParams(insertType, point != null, insert != null);
            return new PutPostParameters(insertType, point);
        }

        private static void checkQueryParams(final InsertParameter insertParameterValue,
                                             final boolean pointUsed, final boolean insertUsed) {
            RestconfDocumentedException.throwIf(pointUsed && !insertUsed, RestconfError.ErrorType.PROTOCOL,
                    ErrorTag.MISSING_ELEMENT, "Point parameter can't be used without insert parameter.");
            RestconfDocumentedException.throwIf((InsertParameter.FIRST.equals(insertParameterValue) ||
                            InsertParameter.LAST.equals(insertParameterValue)) && !pointUsed, ErrorType.PROTOCOL,
                    ErrorTag.BAD_ELEMENT, "If the values 'before' or 'after' are used, then a 'point' query parameter "
                            + "for the 'insert' query parameter MUST also be present");
        }
    }
}