/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;

public final class ServerUtil {

    private ServerUtil() {
        // Hidden on purpose
    }

    /**
     * If provided parameter condition is false, throw a {@link RequestException}.
     *
     * @param condition {@code boolean}
     * @param message message thrown if condition is false
     * @throws RequestException if condition is false
     */
    public static void verify(final boolean condition, final String message) throws RequestException {
        if (!condition) {
            throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE, message);
        }
    }

    /**
     * Check if provided value is not null.
     *
     * @param value node value
     * @param qname node QName
     * @return provided value if it is not null, otherwise throws RequestException
     * @throws RequestException if the value is null
     */
    static <T> T requireNonNullValue(final T value, final QName qname) throws RequestException {
        if (value == null) {
            throw new RequestException(ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE,
                "Missing required schema node " + qname);
        }
        return value;
    }
}
