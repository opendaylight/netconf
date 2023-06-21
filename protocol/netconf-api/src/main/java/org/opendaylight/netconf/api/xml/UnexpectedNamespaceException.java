/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import java.io.Serial;
import java.util.Map;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

public class UnexpectedNamespaceException extends DocumentedException {
    @Serial
    private static final long serialVersionUID = 1L;

    public UnexpectedNamespaceException(final String format, final Object... args) {
        this(String.format(format, args), ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
    }

    public UnexpectedNamespaceException(final String message, final ErrorType errorType, final ErrorTag errorTag,
            final ErrorSeverity errorSeverity) {
        this(message, errorType, errorTag, errorSeverity, Map.of());
    }

    public UnexpectedNamespaceException(final String message, final ErrorType errorType, final ErrorTag errorTag,
            final ErrorSeverity errorSeverity, final Map<String, String> errorInfo) {
        super(message, errorType, errorTag, errorSeverity, errorInfo);
    }
}
