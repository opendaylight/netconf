/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.validation;

import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import org.opendaylight.netconf.md.sal.rest.common.RestconfValidationUtils;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;

/**
 * Util class for validations
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
     *            - iterator
     * @return {@link Date}
     */
    public static Date validateAndGetRevision(final Iterator<String> revisionDate) {
        RestconfValidationUtils.checkDocumentedError(revisionDate.hasNext(), ErrorType.PROTOCOL,
                ErrorTag.INVALID_VALUE, "Revision date must be supplied.");
        try {
            return SimpleDateFormatUtil.getRevisionFormat().parse(revisionDate.next());
        } catch (final ParseException e) {
            throw new RestconfDocumentedException("Supplied revision is not in expected date format YYYY-mm-dd", e);
        }
    }

    /**
     * Validation of name.
     *
     * @param moduleName
     *            - iterator
     * @return {@link String}
     */
    public static String validateAndGetModulName(final Iterator<String> moduleName) {
        RestconfValidationUtils.checkDocumentedError(moduleName.hasNext(), ErrorType.PROTOCOL,
                ErrorTag.INVALID_VALUE, "Module name must be supplied.");
        return moduleName.next();
    }

}
