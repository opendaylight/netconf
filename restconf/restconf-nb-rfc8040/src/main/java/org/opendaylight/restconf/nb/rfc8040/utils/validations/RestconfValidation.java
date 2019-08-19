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
import java.util.Locale;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
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
        RestconfDocumentedException.throwIf(!revisionDate.hasNext(), "Revision date must be supplied.",
            ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
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
        RestconfDocumentedException.throwIf(!moduleName.hasNext(), "Module name must be supplied.", ErrorType.PROTOCOL,
            ErrorTag.INVALID_VALUE);
        final String name = moduleName.next();

        RestconfDocumentedException.throwIf(
            name.isEmpty() || !ParserBuilderConstants.Deserializer.IDENTIFIER_FIRST_CHAR.matches(name.charAt(0)),
            "Identifier must start with character from set 'a-zA-Z_", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        RestconfDocumentedException.throwIf(name.toUpperCase(Locale.ROOT).startsWith("XML"),
            "Identifier must NOT start with XML ignore case.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        RestconfDocumentedException.throwIf(
            !ParserBuilderConstants.Deserializer.IDENTIFIER.matchesAllOf(name.substring(1)),
            "Supplied name has not expected identifier format.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);

        return name;
    }

}
