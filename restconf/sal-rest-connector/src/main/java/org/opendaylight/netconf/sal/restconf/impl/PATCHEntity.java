/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.base.Preconditions;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class PATCHEntity {

    private final String operation;
    private final String editId;
    private final YangInstanceIdentifier targetNode;
    private final NormalizedNode<?,?> node;

    /**
     * Constructor to create PATCHEntity for PATCH operations which require value leaf representing data to be present.
     * @param editId Id of PATCH edit
     * @param operation PATCH edit operation
     * @param targetNode Target node for PATCH edit operation
     * @param node Data defined by value leaf used by edit operation
     */
    public PATCHEntity(final String editId, final String operation, final YangInstanceIdentifier targetNode, final
    NormalizedNode<?, ?> node) {
        this.editId = Preconditions.checkNotNull(editId);
        this.operation = Preconditions.checkNotNull(operation);
        this.targetNode = Preconditions.checkNotNull(targetNode);
        this.node = Preconditions.checkNotNull(node);
    }

    /**
     * Constructor to create PATCHEntity for PATCH operations which do not allow value leaf representing data to be
     * present. <code>node</code> is set to <code>null</code> meaning that data are not allowed for edit operation.
     * @param editId Id of PATCH edit
     * @param operation PATCH edit operation
     * @param targetNode Target node for PATCH edit operation
     */
    public PATCHEntity(final String editId, final String operation, final YangInstanceIdentifier targetNode) {
        this.editId = Preconditions.checkNotNull(editId);
        this.operation = Preconditions.checkNotNull(operation);
        this.targetNode = Preconditions.checkNotNull(targetNode);
        this.node = null;
    }

    public String getOperation() {
        return operation;
    }

    public String getEditId() {
        return editId;
    }

    public YangInstanceIdentifier getTargetNode() {
        return targetNode;
    }

    public NormalizedNode<?, ?> getNode() {
        return node;
    }

}
