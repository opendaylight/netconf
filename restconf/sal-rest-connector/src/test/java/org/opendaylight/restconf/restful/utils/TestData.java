/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
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
    final UnkeyedListEntryNode unkeyedListEntryNode;
    final LeafNode contentLeaf;
    final LeafNode contentLeaf2;
    final MapEntryNode checkData;
    final SchemaPath rpc;
    final SchemaPath errorRpc;
    final ContainerNode input;
    final ContainerNode output;

    TestData() {
        final QName base = QName.create("ns", "2016-02-28", "base");
        final QName listQname = QName.create(base, "list");
        final QName listKeyQName = QName.create(base, "list-key");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQName, "keyValue");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey2 =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQName, "keyValue2");
        final LeafNode<Object> content = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "leaf-content")))
                .withValue("content")
                .build();
        final LeafNode<Object> content2 = Builders.leafBuilder()
                .withNodeIdentifier(
                        new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "leaf-content-different")))
                .withValue("content-different")
                .build();
        final DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?> dataContainer =
                Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "identifier")))
                .withValue("id")
                .build();
        unkeyedListEntryNode = Builders.unkeyedListEntryBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "list")))
                .withChild(dataContainer)
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
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "list")))
                .withChild(data)
                .build();
        listData2 = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "list")))
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
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "content")))
                .withValue("test")
                .build();
        contentLeaf2 = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "content2")))
                .withValue("test2")
                .build();
        data3 = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "container")))
                .withChild(contentLeaf)
                .build();
        data4 = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "container2")))
                .withChild(contentLeaf2)
                .build();


        final QName rpcQname = QName.create("ns", "2015-02-28", "test-rpc");
        final QName errorRpcQname = QName.create(rpcQname, "error-rpc");
        rpc = SchemaPath.create(true, rpcQname);
        errorRpc = SchemaPath.create(true, errorRpcQname);
        final LeafNode contentLeafNode = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "content")))
                .withValue("test")
                .build();
        input = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "input")))
                .withChild(contentLeafNode)
                .build();
        final LeafNode resultLeafNode = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "content")))
                .withValue("operation result")
                .build();
        output = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "output")))
                .withChild(resultLeafNode)
                .build();
    }
}