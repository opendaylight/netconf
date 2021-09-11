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

public class PatchStatusEntity {
    private final List<YangNetconfError> editErrors;
    private final String editId;
    private final boolean ok;

    public PatchStatusEntity(final String editId, final boolean ok, final List<YangNetconfError> editErrors) {
        // FIXME: nullability
        this.editId = editId;
        this.ok = ok;
        // FIXME: immutability
        this.editErrors = editErrors;
    }

    public String getEditId() {
        return editId;
    }

    public boolean isOk() {
        return ok;
    }

    public List<YangNetconfError> getEditErrors() {
        return editErrors;
    }
}
