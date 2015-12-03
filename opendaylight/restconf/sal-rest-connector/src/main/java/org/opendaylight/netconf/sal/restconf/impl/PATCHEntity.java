/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class PATCHEntity {

    private String operation;
    private YangInstanceIdentifier targetNode;
    private NormalizedNode<?,?> node;

    public PATCHEntity(String operation, YangInstanceIdentifier targetNode, NormalizedNode<?, ?> node) {
        this.operation = operation;
        this.targetNode = targetNode;
        this.node = node;
    }
}
