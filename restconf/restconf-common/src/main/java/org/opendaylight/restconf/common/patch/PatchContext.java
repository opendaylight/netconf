/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.patch;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;

public class PatchContext {
    private final InstanceIdentifierContext context;
    private final List<PatchEntity> data;
    private final String patchId;

    public PatchContext(final InstanceIdentifierContext context, final List<PatchEntity> data, final String patchId) {
        this.context = requireNonNull(context);
        this.data = requireNonNull(data);
        this.patchId = requireNonNull(patchId);
    }

    public InstanceIdentifierContext getInstanceIdentifierContext() {
        return context;
    }

    public List<PatchEntity> getData() {
        return data;
    }

    public String getPatchId() {
        return patchId;
    }
}
