/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class TestData {

    final QName root = QName.create("ns", "2016-06-24", "root");
    final QName node = QName.create(root, "node");
    final QName key = QName.create(root, "key");
    final YangInstanceIdentifier failPath = YangInstanceIdentifier.EMPTY;
    final Exception exc = new Exception("failed");
    final YangInstanceIdentifier path;
    final MapEntryNode data;
    final ContainerNode rpcContent;
    final ContainerNode rpcResponseContent;
    final SchemaPath rpcPath;

    public TestData() {
        final YangInstanceIdentifier.NodeIdentifierWithPredicates key =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(node, this.key, "key");
        data = Builders.mapEntryBuilder()
                .withNodeIdentifier(key)
                .build();
        rpcContent = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(root))
                .build();
        final LeafNode<Object> leaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(this.node))
                .withValue("node")
                .build();
        rpcResponseContent = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(root))
                .withChild(leaf)
                .build();
        rpcPath = SchemaPath.create(true, root);
        path = YangInstanceIdentifier.builder()
                .node(root)
                .node(key)
                .build();
    }
}
