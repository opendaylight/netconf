/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.common.patch;

import java.util.List;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class PatchStatusContext {
    private final EffectiveModelContext context;
    private final String patchId;
    private final List<PatchStatusEntity> editCollection;
    private boolean ok;
    private List<RestconfError> globalErrors;

    public PatchStatusContext(final String patchId, final List<PatchStatusEntity> editCollection,
            final boolean ok, final List<RestconfError> globalErrors, final EffectiveModelContext context) {
        this.patchId = patchId;
        this.editCollection = editCollection;
        this.ok = ok;
        this.globalErrors = globalErrors;
        this.context = context;
    }

    public PatchStatusContext(final String patchId, final List<PatchStatusEntity> editCollection,
            final boolean ok, final List<RestconfError> globalErrors) {
        this.patchId = patchId;
        this.editCollection = editCollection;
        this.ok = ok;
        this.globalErrors = globalErrors;
        this.context = null;
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

    public List<RestconfError> getGlobalErrors() {
        return globalErrors;
    }

    public EffectiveModelContext getContext() {
        return context;
    }
}
