/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.validations;

import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Iterator;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.validation.RestconfValidationUtils;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.yangtools.yang.common.Revision;

/**
 * Util class for validations.
 *
 */
public final class RestconfValidation {

    private RestconfValidation() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Validation and parsing of revision.
     *
     * @param revisionDate
     *             iterator
     * @return {@link Date}
     */
    public static Revision validateAndGetRevision(final Iterator<String> revisionDate) {
        RestconfValidationUtils.checkDocumentedError(revisionDate.hasNext(), ErrorType.PROTOCOL,
                ErrorTag.INVALID_VALUE, "Revision date must be supplied.");
        try {
            return Revision.of(revisionDate.next());
        } catch (final DateTimeParseException e) {
            throw new RestconfDocumentedException("Supplied revision is not in expected date format YYYY-mm-dd", e);
        }
    }

    /**
     * Validation of name.
     *
     * @param moduleName
     *             iterator
     * @return {@link String}
     */
    public static String validateAndGetModulName(final Iterator<String> moduleName) {
        RestconfValidationUtils.checkDocumentedError(
                moduleName.hasNext(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Module name must be supplied."
        );

        final String name = moduleName.next();

        RestconfValidationUtils.checkDocumentedError(
                !name.isEmpty() && ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR.matches(name.charAt(0)),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Identifier must start with character from set 'a-zA-Z_"
        );

        RestconfValidationUtils.checkDocumentedError(
                !name.toUpperCase().startsWith("XML"),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Identifier must NOT start with XML ignore case."
        );

        RestconfValidationUtils.checkDocumentedError(
                ParserBuilderConstants.Deserializer.IDENTIFIER.matchesAllOf(name.substring(1)),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Supplied name has not expected identifier format."
        );

        return name;
    }

}
