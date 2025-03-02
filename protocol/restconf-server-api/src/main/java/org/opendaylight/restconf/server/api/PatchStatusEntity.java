/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.opendaylight.netconf.databind.RequestError;

public class PatchStatusEntity {
    private final String editId;
    private final List<RequestError> editErrors;
    private final boolean ok;

    public PatchStatusEntity(final String editId, final boolean ok, final List<RequestError> editErrors) {
        this.editId = requireNonNull(editId);
        this.ok = ok;
        this.editErrors = editErrors;
    }

    public String getEditId() {
        return editId;
    }

    public boolean isOk() {
        return ok;
    }

    public List<RequestError> getEditErrors() {
        return editErrors;
    }
}
