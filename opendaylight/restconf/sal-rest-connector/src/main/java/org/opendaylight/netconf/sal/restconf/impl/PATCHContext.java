/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import java.util.List;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class PATCHContext {

    private final InstanceIdentifierContext<? extends SchemaNode> context;
    private final List<PATCHEntity> data;
    private final String patchId;

    public PATCHContext(final InstanceIdentifierContext<? extends SchemaNode> context,
                        final List<PATCHEntity> data, final String patchId) {
        this.context = context;
        this.data = data;
        this.patchId = patchId;
    }

    public InstanceIdentifierContext<? extends SchemaNode> getInstanceIdentifierContext() {
        return context;
    }

    public List<PATCHEntity> getData() {
        return data;
    }

    public String getPatchId() {
        return patchId;
    }
}
