/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.patch;

import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfError;

/**
 * Unchecked exception to communicate error information when processing of YANG PATCH fails.
 */
public class YangPatchDocumentedException extends WebApplicationException {
    private final @NonNull String patchId;
    private final @NonNull List<PatchStatusEntity> editCollection;
    private final boolean ok;
    private final @Nullable List<RestconfError> globalErrors;
    private final Status status;

    /**
     * Constructs an instance with a patch status context.
     *
     * @param patchStatusContext    Information about failed YANG PATCH.
     */
    public YangPatchDocumentedException(final PatchStatusContext patchStatusContext) {
        patchId = patchStatusContext.patchId();
        editCollection = List.copyOf(patchStatusContext.editCollection());
        ok = patchStatusContext.ok();
        final var errors = patchStatusContext.globalErrors();
        globalErrors = errors != null ? List.copyOf(errors) : null;
        status = null;
    }

    public Status status() {
        return status;
    }

    public String patchId() {
        return patchId;
    }

    public List<PatchStatusEntity> editCollection() {
        return editCollection;
    }

    public boolean ok() {
        return ok;
    }

    public List<RestconfError> globalErrors() {
        return globalErrors;
    }
}
