/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.patch;

import java.util.List;
import org.opendaylight.yangtools.yang.data.api.YangNetconfError;

public class PatchStatusContext {

    private final String patchId;
    private final List<PatchStatusEntity> editCollection;
    private final boolean ok;
    private final List<YangNetconfError> globalErrors;

    public PatchStatusContext(final String patchId, final List<PatchStatusEntity> editCollection,
                              final boolean ok, final List<YangNetconfError> globalErrors) {
        // FIXME: nullability
        this.patchId = patchId;
        // FIXME: immutability
        this.editCollection = editCollection;
        this.ok = ok;
        // FIXME: immutability
        this.globalErrors = globalErrors;
    }

    public String getPatchId() {
        return patchId;
    }

    public List<PatchStatusEntity> getEditCollection() {
        return editCollection;
    }

    public boolean isOk() {
        return ok;
    }

    public List<YangNetconfError> getGlobalErrors() {
        return globalErrors;
    }
}
