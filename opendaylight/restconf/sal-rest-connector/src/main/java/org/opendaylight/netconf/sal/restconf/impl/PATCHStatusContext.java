/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import java.util.List;

public class PATCHStatusContext {

    private final String patchId;
    private final List<PATCHStatusEntity> editCollection;
    private boolean ok;
    private List<RestconfError> globalErrors;

    public PATCHStatusContext(final String patchId, final List<PATCHStatusEntity> editCollection, final boolean ok,
                              final List<RestconfError> globalErrors) {
        this.patchId = patchId;
        this.editCollection = editCollection;
        this.ok = ok;
        this.globalErrors = globalErrors;
    }

    public String getPatchId() {
        return patchId;
    }

    public List<PATCHStatusEntity> getEditCollection() {
        return editCollection;
    }

    public boolean isOk() {
        return ok;
    }

    public List<RestconfError> getGlobalErrors() {
        return globalErrors;
    }
}
