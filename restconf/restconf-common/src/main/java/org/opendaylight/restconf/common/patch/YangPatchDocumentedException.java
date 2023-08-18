/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.patch;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * Unchecked exception to communicate error information when processing of YANG PATCH fails.
 */
public class YangPatchDocumentedException extends WebApplicationException {
    private final PatchStatusContext patchStatusContext;
    private final Status status;

    /**
     * Constructs an instance with a patch status context and HTTP status.
     *
     * @param patchStatusContext    Information about failed YANG PATCH.
     * @param status                The HTTP status.
     */
    public YangPatchDocumentedException(final PatchStatusContext patchStatusContext, final Status status) {
        this.patchStatusContext = patchStatusContext;
        this.status = status;
    }

    /**
     * Constructs an instance with an error message, patch status context, HTTP status and exception cause.
     *
     * @param message               A string which provides a plain text string describing the error.
     * @param patchStatusContext    Information about failed YANG PATCH.
     * @param status                The HTTP status.
     * @param cause                 The underlying exception cause.
     */
    public YangPatchDocumentedException(final String message, final PatchStatusContext patchStatusContext,
            final Status status, final Throwable cause) {
        super(message, cause);
        this.patchStatusContext = patchStatusContext;
        this.status = status;
    }

    /**
     * Constructs an instance with an error message and patch status context.
     *
     * @param message               A string which provides a plain text string describing the error.
     * @param patchStatusContext    Information about failed YANG PATCH.
     */
    public YangPatchDocumentedException(final String message, final PatchStatusContext patchStatusContext) {
        super(message);
        this.patchStatusContext = patchStatusContext;
        this.status = null;
    }

    /**
     * Constructs an instance with a patch status context and exception cause.
     *
     * @param patchStatusContext    Information about failed YANG PATCH.
     * @param cause                 The underlying exception cause.
     */
    public YangPatchDocumentedException(final PatchStatusContext patchStatusContext, final Throwable cause) {
        super(cause);
        this.patchStatusContext = patchStatusContext;
        this.status = null;
    }

    /**
     * Constructs an instance with a patch status context.
     *
     * @param patchStatusContext    Information about failed YANG PATCH.
     */
    public YangPatchDocumentedException(final PatchStatusContext patchStatusContext) {
        this.patchStatusContext = patchStatusContext;
        this.status = null;
    }

    public Status getStatus() {
        return status;
    }

    public PatchStatusContext getPatchStatusContext() {
        return patchStatusContext;
    }
}
