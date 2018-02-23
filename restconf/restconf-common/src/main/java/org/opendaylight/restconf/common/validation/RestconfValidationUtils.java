/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.common.validation;

import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;

/**
 * sal-rest-connector
 * org.opendaylight.controller.md.sal.rest.common
 *
 * <p>
 * Utility class is centralizing all needed validation functionality for a Restconf osgi module.
 * All methods have to throw {@link RestconfDocumentedException} only, which is a representation
 * for all error situation followed by restconf-netconf specification.
 * See also <a href="https://tools.ietf.org/html/draft-bierman-netconf-restconf-02">RESTCONF</a>.
 */
public final class RestconfValidationUtils {

    private RestconfValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Method returns {@link RestconfDocumentedException} for a false condition.
     *
     * @param condition - condition for rise {@link RestconfDocumentedException}
     * @param type      - input {@link ErrorType} for create {@link RestconfDocumentedException}
     * @param tag       - input {@link ErrorTag} for create {@link RestconfDocumentedException}
     * @param message   - input error message for create {@link RestconfDocumentedException}
     */
    public static void checkDocumentedError(final boolean condition, final ErrorType type,
            final ErrorTag tag, final String message) {
        if (!condition) {
            throw new RestconfDocumentedException(message, type, tag);
        }
    }

    /**
     * Method returns {@link RestconfDocumentedException} if value is NULL or same input value.
     * {@link ErrorType} is relevant for server application layer
     * {@link ErrorTag} is 404 data-missing
     * See also <a href="https://tools.ietf.org/html/draft-bierman-netconf-restconf-02">RESTCONF</a>.
     *
     * @param value         - some value from {@link org.opendaylight.yangtools.yang.model.api.Module}
     * @param moduleName    - name of {@link org.opendaylight.yangtools.yang.model.api.Module}
     * @return              - T value (same input value)
     */
    public static <T> T checkNotNullDocumented(final T value, final String moduleName) {
        if (value == null) {
            final String errMsg = "Module " + moduleName + " was not found.";
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
        }
        return value;
    }
}
