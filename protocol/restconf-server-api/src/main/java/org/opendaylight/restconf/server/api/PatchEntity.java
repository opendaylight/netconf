/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static org.opendaylight.restconf.server.api.ServerUtil.requireNonNullValue;

import com.google.common.annotations.Beta;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

@Beta
public class PatchEntity {
    private static final QName QN_EDIT_ID = QName.create(Edit.QNAME, "edit-id");
    private static final QName QN_OPERATION = QName.create(Edit.QNAME, "operation");
    private static final QName QN_TARGET = QName.create(Edit.QNAME, "target");
    private static final QName QN_VALUE = QName.create(Edit.QNAME, "value");

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
     * @throws RequestException if any of provided parameters are null
     */
    public PatchEntity(final String editId, final Operation operation, final YangInstanceIdentifier targetNode,
                       final NormalizedNode node) throws RequestException {
        this.editId = requireNonNullValue(editId, QN_EDIT_ID);
        this.operation = requireNonNullValue(operation, QN_OPERATION);
        this.targetNode = requireNonNullValue(targetNode, QN_TARGET);
        this.node = requireNonNullValue(node, QN_VALUE);
    }

    /**
     * Constructor to create PatchEntity for Patch operations which do not allow value leaf representing data to be
     * present. <code>node</code> is set to <code>null</code> meaning that data are not allowed for edit operation.
     * @param editId Id of Patch edit
     * @param operation Patch edit operation
     * @param targetNode Target node for Patch edit operation
     * @throws RequestException if any of provided parameters are null
     */
    public PatchEntity(final String editId, final Operation operation, final YangInstanceIdentifier targetNode)
            throws RequestException {
        this.editId = requireNonNullValue(editId, QN_EDIT_ID);
        this.operation = requireNonNullValue(operation, QN_OPERATION);
        this.targetNode = requireNonNullValue(targetNode, QN_TARGET);
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
