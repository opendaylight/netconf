/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.patch;

import static java.util.Objects.requireNonNull;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class PatchEntity {
    private final Operation operation;
    private final String editId;
    private final YangInstanceIdentifier targetNode;
    private final NormalizedNode node;

    /**
     * Constructor to create PatchEntity for Patch operations which require value leaf representing data to be present.
     * @param editId Id of Patch edit
     * @param operation Patch edit operation
     * @param targetNode Target node for Patch edit operation
     * @param node Data defined by value leaf used by edit operation
     */
    public PatchEntity(final String editId, final Operation operation, final YangInstanceIdentifier targetNode,
                       final NormalizedNode node) {
        this.editId = requireNonNull(editId);
        this.operation = requireNonNull(operation);
        this.targetNode = requireNonNull(targetNode);
        this.node = requireNonNull(node);
    }

    /**
     * Constructor to create PatchEntity for Patch operations which do not allow value leaf representing data to be
     * present. <code>node</code> is set to <code>null</code> meaning that data are not allowed for edit operation.
     * @param editId Id of Patch edit
     * @param operation Patch edit operation
     * @param targetNode Target node for Patch edit operation
     */
    public PatchEntity(final String editId, final Operation operation, final YangInstanceIdentifier targetNode) {
        this.editId = requireNonNull(editId);
        this.operation = requireNonNull(operation);
        this.targetNode = requireNonNull(targetNode);
        node = null;
    }

    public Operation getOperation() {
        return operation;
    }

    public String getEditId() {
        return editId;
    }

    public YangInstanceIdentifier getTargetNode() {
        return targetNode;
    }

    public NormalizedNode getNode() {
        return node;
    }

}
