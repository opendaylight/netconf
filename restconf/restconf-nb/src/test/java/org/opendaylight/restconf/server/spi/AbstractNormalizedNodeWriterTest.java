/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

abstract class AbstractNormalizedNodeWriterTest {
    static final NodeIdentifier CONTAINER_NID = NodeIdentifier.create(QName.create("namespace", "container"));
    static final NodeIdentifier KEY_FIELD_NID = NodeIdentifier.create(QName.create("namespace", "key-field"));
    static final NodeIdentifier LIST_NID = NodeIdentifier.create(QName.create("namespace", "list"));
    static final NodeIdentifier LEAF_SET_NID = NodeIdentifier.create(QName.create("namespace", "leaf-set"));

    // FIXME: make these proper constants
    final String leafSetEntryNodeValue = "leaf-set-value";
    final String keyLeafNodeValue = "key-value";

    final NodeIdentifierWithPredicates mapEntryNodeIdentifier = NodeIdentifierWithPredicates.of(
        QName.create("namespace", "list-entry"), KEY_FIELD_NID.getNodeType(), keyLeafNodeValue);
    final NodeWithValue<String> leafSetEntryNodeIdentifier = new NodeWithValue<>(
        QName.create("namespace", "leaf-set-entry"), leafSetEntryNodeValue);

    final LeafNode<String> keyLeafNodeData = ImmutableNodes.leafNode(KEY_FIELD_NID, keyLeafNodeValue);
    final LeafSetEntryNode<String> leafSetEntryNodeData = ImmutableNodes.<String>newLeafSetEntryBuilder()
        .withNodeIdentifier(leafSetEntryNodeIdentifier)
        .withValue(leafSetEntryNodeValue)
        .build();
    final SystemLeafSetNode<String> leafSetNodeData = ImmutableNodes.<String>newSystemLeafSetBuilder()
        .withNodeIdentifier(LEAF_SET_NID)
        .withChild(leafSetEntryNodeData)
        .build();
    final ContainerNode containerNodeData = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(CONTAINER_NID)
        .withChild(leafSetNodeData)
        .build();
}
