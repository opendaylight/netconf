/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class TestData {

    public final LogicalDatastoreType datastore = LogicalDatastoreType.CONFIGURATION;
    public final String errorMessage = "<errors xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\">\n" +
            "  <error>\n" +
            "    <error-type>protocol</error-type>\n" +
            "    <error-tag>lock-denied</error-tag>\n" +
            "    <error-message>Lock failed, lock already held</error-message>\n" +
            "  </error>\n" +
            "</errors>";

    public final YangInstanceIdentifier path;
    public final YangInstanceIdentifier path2;
    public final YangInstanceIdentifier path3;
    public final YangInstanceIdentifier nonExistPath;
    public final YangInstanceIdentifier errorPath;
    public final MapEntryNode data;
    public final MapEntryNode data2;
    public final SchemaPath rpc;
    public final SchemaPath errorRpc;
    public final ContainerNode input;
    public final ContainerNode output;
    public final MapNode listData;
    public final UnkeyedListNode listUnkeyedData;
    public final UnkeyedListEntryNode unkeyedListEntryNode;

    public TestData() {
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
        DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?> dataContainer = Builders.leafBuilder()
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
                .withNodeIdentifier(nodeWithKey2)
                .withChild(content)
                .build();
        listData = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "list")))
                .withChild(data)
                .withChild(data2)
                .build();
        listUnkeyedData = Builders.unkeyedListBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "list")))
                .withChild(unkeyedListEntryNode)
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
        nonExistPath = YangInstanceIdentifier.builder()
                .node(QName.create(base, "non-exist"))
                .node(listQname)
                .node(nodeWithKey)
                .build();
        errorPath = YangInstanceIdentifier.builder()
                .node(QName.create(base, "error"))
                .build();

        final QName rpcQname = QName.create("ns", "2015-02-28", "test-rpc");
        final QName errorRpcQname = QName.create(rpcQname, "error-rpc");
        rpc = SchemaPath.create(true, rpcQname);
        errorRpc = SchemaPath.create(true, errorRpcQname);
        LeafNode contentLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "content")))
                .withValue("test")
                .build();
        input = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "input")))
                .withChild(contentLeaf)
                .build();
        LeafNode resultLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "content")))
                .withValue("operation result")
                .build();
        output = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "output")))
                .withChild(resultLeaf)
                .build();
    }
}