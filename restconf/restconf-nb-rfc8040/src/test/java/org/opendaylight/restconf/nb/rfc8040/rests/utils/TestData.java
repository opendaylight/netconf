/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

class TestData {

    final YangInstanceIdentifier path;
    final YangInstanceIdentifier path2;
    final YangInstanceIdentifier path3;
    final MapEntryNode data;
    final MapEntryNode data2;
    final ContainerNode data3;
    final ContainerNode data4;
    final MapNode listData;
    final MapNode listData2;
    final OrderedMapNode orderedMapNode1;
    final OrderedMapNode orderedMapNode2;
    final LeafNode contentLeaf;
    final LeafNode contentLeaf2;
    final MapEntryNode checkData;
    final SchemaPath rpc;
    final SchemaPath errorRpc;
    final ContainerNode input;
    final ContainerNode output;
    final LeafSetNode<String> leafSetNode1;
    final LeafSetNode<String> leafSetNode2;
    final LeafSetNode<String> orderedLeafSetNode1;
    final LeafSetNode<String> orderedLeafSetNode2;
    final YangInstanceIdentifier leafSetNodePath;
    final UnkeyedListNode unkeyedListNode1;
    final UnkeyedListNode unkeyedListNode2;
    final UnkeyedListEntryNode unkeyedListEntryNode1;
    final UnkeyedListEntryNode unkeyedListEntryNode2;

    final QName base = QName.create("ns", "2016-02-28", "base");
    final QName listKeyQName = QName.create(base, "list-key");
    final QName leafListQname = QName.create(base, "leaf-list");
    final QName listQname = QName.create(base, "list");

    TestData() {
        final NodeIdentifierWithPredicates nodeWithKey =
                NodeIdentifierWithPredicates.of(listQname, listKeyQName, "keyValue");
        final NodeIdentifierWithPredicates nodeWithKey2 =
                NodeIdentifierWithPredicates.of(listQname, listKeyQName, "keyValue2");
        final LeafNode<Object> content = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(base, "leaf-content")))
                .withValue("content")
                .build();
        final LeafNode<Object> content2 = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(base, "leaf-content-different")))
                .withValue("content-different")
                .build();
        final DataContainerChild<?, ?> dataContainer =
                Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(listQname, "identifier")))
                .withValue("id")
                .build();
        data = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content)
                .build();
        data2 = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content2)
                .build();
        checkData = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content2)
                .withChild(content)
                .build();
        listData = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(listQname, "list")))
                .withChild(data)
                .build();
        listData2 = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(listQname, "list")))
                .withChild(data)
                .withChild(data2)
                .build();
        path = YangInstanceIdentifier.builder()
                .node(QName.create(base, "cont"))
                .node(listQname)
                .node(nodeWithKey)
                .build();
        path2 = YangInstanceIdentifier.builder()
                .node(QName.create(base, "cont"))
                .node(listQname)
                .node(nodeWithKey2)
                .build();
        path3 = YangInstanceIdentifier.builder()
                .node(QName.create(base, "cont"))
                .node(listQname)
                .build();
        contentLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(base, "content")))
                .withValue("test")
                .build();
        contentLeaf2 = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(base, "content2")))
                .withValue("test2")
                .build();
        data3 = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(base, "container")))
                .withChild(contentLeaf)
                .build();
        data4 = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "container2")))
                .withChild(contentLeaf2)
                .build();

        leafSetNodePath = YangInstanceIdentifier.builder().node(QName.create(base, "cont"))
                .node(leafListQname).build();
        leafSetNode1 = Builders.<String>leafSetBuilder().withNodeIdentifier(new NodeIdentifier(
                leafListQname)).withChildValue("one").withChildValue("two").build();

        leafSetNode2 = Builders.<String>leafSetBuilder().withNodeIdentifier(new NodeIdentifier(
                leafListQname)).withChildValue("three").build();

        orderedLeafSetNode1 = Builders.<String>orderedLeafSetBuilder().withNodeIdentifier(
                new NodeIdentifier(leafListQname)).withChildValue("one")
                .withChildValue("two").build();
        orderedLeafSetNode2 = Builders.<String>orderedLeafSetBuilder().withNodeIdentifier(
                new NodeIdentifier(leafListQname)).withChildValue("three")
                .withChildValue("four").build();

        orderedMapNode1 = Builders.orderedMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(listQname)).withChild(data).build();

        orderedMapNode2 = Builders.orderedMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(listQname)).withChild(data)
                .withChild(data2).build();

        unkeyedListEntryNode1 = Builders.unkeyedListEntryBuilder().withNodeIdentifier(
                new NodeIdentifier(listQname)).withChild(content).build();
        unkeyedListNode1 = Builders.unkeyedListBuilder().withNodeIdentifier(
                new NodeIdentifier(listQname)).withChild(unkeyedListEntryNode1).build();

        unkeyedListEntryNode2 = Builders.unkeyedListEntryBuilder().withNodeIdentifier(
                new YangInstanceIdentifier.NodeIdentifier(listQname)).withChild(content2).build();
        unkeyedListNode2 = Builders.unkeyedListBuilder().withNodeIdentifier(
                new YangInstanceIdentifier.NodeIdentifier(listQname)).withChild(unkeyedListEntryNode2).build();

        final QName rpcQname = QName.create("ns", "2015-02-28", "test-rpc");
        final QName errorRpcQname = QName.create(rpcQname, "error-rpc");
        rpc = SchemaPath.create(true, rpcQname);
        errorRpc = SchemaPath.create(true, errorRpcQname);
        final LeafNode contentLeafNode = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(rpcQname, "content")))
                .withValue("test")
                .build();
        input = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(rpcQname, "input")))
                .withChild(contentLeafNode)
                .build();
        final LeafNode resultLeafNode = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(rpcQname, "content")))
                .withValue("operation result")
                .build();
        output = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(rpcQname, "output")))
                .withChild(resultLeafNode)
                .build();
    }
}
