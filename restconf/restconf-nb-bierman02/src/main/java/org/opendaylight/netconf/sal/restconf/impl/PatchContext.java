/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.base.Preconditions;
import java.util.List;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class PatchContext {

    private final InstanceIdentifierContext<? extends SchemaNode> context;
    private final List<PatchEntity> data;
    private final String patchId;

    public PatchContext(final InstanceIdentifierContext<? extends SchemaNode> context,
                        final List<PatchEntity> data, final String patchId) {
        this.context = Preconditions.checkNotNull(context);
        this.data = Preconditions.checkNotNull(data);
        this.patchId = Preconditions.checkNotNull(patchId);
    }

    public InstanceIdentifierContext<? extends SchemaNode> getInstanceIdentifierContext() {
        return context;
    }

    public List<PatchEntity> getData() {
        return data;
    }

    public String getPatchId() {
        return patchId;
    }
}
