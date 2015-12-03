/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class PATCHContext {

    private final InstanceIdentifierContext<? extends SchemaNode> context;

    public PATCHContext(final InstanceIdentifierContext<? extends SchemaNode> context) {
        this.context = context;
    }

    public InstanceIdentifierContext<? extends SchemaNode> getInstanceIdentifierContext() {
        return context;
    }
}
