/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serial;
import java.util.Map;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;

/**
 * Checked exception to communicate an error that needs to be sent to the netconf client.
 */
public class NetconfDocumentedException extends DocumentedException {
    @Serial
    private static final long serialVersionUID = 1L;

    public NetconfDocumentedException(final String message) {
        super(message);
    }

    public NetconfDocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag,
                                      final ErrorSeverity errorSeverity) {
        super(message, errorType, errorTag, errorSeverity);
    }

    public NetconfDocumentedException(final String message, final ErrorType errorType, final ErrorTag errorTag,
                                      final ErrorSeverity errorSeverity, final Map<String, String> errorInfo) {
        super(message, errorType, errorTag, errorSeverity, errorInfo);
    }

    public NetconfDocumentedException(final String message, final Exception cause, final ErrorType errorType,
                                      final ErrorTag errorTag, final ErrorSeverity errorSeverity) {
        super(message, cause, errorType, errorTag, errorSeverity);
    }

    public NetconfDocumentedException(final String message, final Exception cause, final ErrorType errorType,
                                      final ErrorTag errorTag, final ErrorSeverity errorSeverity,
                                      final Map<String, String> errorInfo) {
        super(message, cause, errorType, errorTag, errorSeverity, errorInfo);
    }

    @SuppressFBWarnings("BC_UNCONFIRMED_CAST_OF_RETURN_VALUE")
    public NetconfDocumentedException(final DocumentedException exception) {
        super(exception.getMessage(), (Exception) exception.getCause(), exception.getErrorType(),
                exception.getErrorTag(), exception.getErrorSeverity(), exception.getErrorInfo());
    }

    public static NetconfDocumentedException fromXMLDocument(final Document fromDoc) {
        return new NetconfDocumentedException(DocumentedException.fromXMLDocument(fromDoc));
    }
}
