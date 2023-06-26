/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.nb.rfc8040.ContentParam;
import org.opendaylight.restconf.nb.rfc8040.WithDefaultsParam;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ReadDataTransactionUtilTest {
    private static final QName BASE = QName.create("ns", "2016-02-28", "base");
    private static final QName LIST_KEY_QNAME = QName.create(BASE, "list-key");
    private static final QName LEAF_LIST_QNAME = QName.create(BASE, "leaf-list");
    private static final QName LIST_QNAME = QName.create(BASE, "list");
    private static final NodeIdentifierWithPredicates NODE_WITH_KEY =
        NodeIdentifierWithPredicates.of(LIST_QNAME, LIST_KEY_QNAME, "keyValue");
    private static final NodeIdentifierWithPredicates NODE_WITH_KEY_2 =
        NodeIdentifierWithPredicates.of(LIST_QNAME, LIST_KEY_QNAME, "keyValue2");
    private static final LeafNode<Object> CONTENT = Builders.leafBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "leaf-content")))
        .withValue("content")
        .build();
    private static final LeafNode<Object> CONTENT_2 = Builders.leafBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "leaf-content-different")))
        .withValue("content-different")
        .build();
    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.builder()
        .node(QName.create(BASE, "cont"))
        .node(LIST_QNAME)
        .node(NODE_WITH_KEY)
        .build();
    private static final YangInstanceIdentifier PATH_2 = YangInstanceIdentifier.builder()
        .node(QName.create(BASE, "cont"))
        .node(LIST_QNAME)
        .node(NODE_WITH_KEY_2)
        .build();
    private static final YangInstanceIdentifier PATH_3 = YangInstanceIdentifier.builder()
        .node(QName.create(BASE, "cont"))
        .node(LIST_QNAME)
        .build();
    private static final MapEntryNode DATA = Builders.mapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT)
        .build();
    private static final MapEntryNode DATA_2 = Builders.mapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT_2)
        .build();
    private static final LeafNode<?> CONTENT_LEAF = Builders.leafBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "content")))
        .withValue("test")
        .build();
    private static final LeafNode<?> CONTENT_LEAF_2 = Builders.leafBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "content2")))
        .withValue("test2")
        .build();
    private static final ContainerNode DATA_3 = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "container")))
        .withChild(CONTENT_LEAF)
        .build();
    private static final ContainerNode DATA_4 = Builders.containerBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE, "container2")))
        .withChild(CONTENT_LEAF_2)
        .build();
    private static final MapNode LIST_DATA = Builders.mapBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(LIST_QNAME, "list")))
        .withChild(DATA)
        .build();
    private static final MapNode LIST_DATA_2 = Builders.mapBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(LIST_QNAME, "list")))
        .withChild(DATA)
        .withChild(DATA_2)
        .build();
    private static final UserMapNode ORDERED_MAP_NODE_1 = Builders.orderedMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(DATA)
        .build();
    private static final UserMapNode ORDERED_MAP_NODE_2 = Builders.orderedMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(DATA)
        .withChild(DATA_2)
        .build();
    private static final MapEntryNode CHECK_DATA = Builders.mapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT_2)
        .withChild(CONTENT)
        .build();
    private static final LeafSetNode<String> LEAF_SET_NODE_1 = Builders.<String>leafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("one")
        .withChildValue("two")
        .build();
    private static final LeafSetNode<String> LEAF_SET_NODE_2 = Builders.<String>leafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("three")
        .build();
    private static final LeafSetNode<String> ORDERED_LEAF_SET_NODE_1 = Builders.<String>orderedLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("one")
        .withChildValue("two")
        .build();
    private static final LeafSetNode<String> ORDERED_LEAF_SET_NODE_2 = Builders.<String>orderedLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("three")
        .withChildValue("four")
        .build();
    private static final YangInstanceIdentifier LEAF_SET_NODE_PATH = YangInstanceIdentifier.builder()
        .node(QName.create(BASE, "cont"))
        .node(LEAF_LIST_QNAME)
        .build();
    private static final UnkeyedListEntryNode UNKEYED_LIST_ENTRY_NODE_1 = Builders.unkeyedListEntryBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(CONTENT)
        .build();
    private static final UnkeyedListEntryNode UNKEYED_LIST_ENTRY_NODE_2 = Builders.unkeyedListEntryBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(LIST_QNAME))
        .withChild(CONTENT_2)
        .build();
    private static final UnkeyedListNode UNKEYED_LIST_NODE_1 = Builders.unkeyedListBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(UNKEYED_LIST_ENTRY_NODE_1)
        .build();
    private static final UnkeyedListNode UNKEYED_LIST_NODE_2 = Builders.unkeyedListBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(LIST_QNAME))
        .withChild(UNKEYED_LIST_ENTRY_NODE_2)
        .build();
    private static final NodeIdentifier NODE_IDENTIFIER =
        new NodeIdentifier(QName.create("ns", "2016-02-28", "container"));

    private RestconfStrategy mdsalStrategy;
    private RestconfStrategy netconfStrategy;
    @Mock
    private NetconfDataTreeService netconfService;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private EffectiveModelContext schemaContext;
    @Mock
    private DOMDataBroker mockDataBroker;

    @Before
    public void setUp() {
        // FIXME: these tests need to be parameterized somehow. The trouble is we need mocking before we invoke
        //        the strategy. This needs some more thought.
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        mdsalStrategy = new MdsalRestconfStrategy(mockDataBroker);
        netconfStrategy = new NetconfRestconfStrategy(netconfService);
    }

    @Test
    public void readDataConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        NormalizedNode normalizedNode = readData(ContentParam.CONFIG, PATH, mdsalStrategy);
        assertEquals(DATA_3, normalizedNode);

        normalizedNode = readData(ContentParam.CONFIG, PATH, netconfStrategy);
        assertEquals(DATA_3, normalizedNode);
    }

    @Test
    public void readAllHavingOnlyConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).get(PATH);
        NormalizedNode normalizedNode = readData(ContentParam.ALL, PATH, mdsalStrategy);
        assertEquals(DATA_3, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, PATH, netconfStrategy);
        assertEquals(DATA_3, normalizedNode);
    }

    @Test
    public void readAllHavingOnlyNonConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(netconfService).get(PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(PATH_2);
        NormalizedNode normalizedNode = readData(ContentParam.ALL, PATH_2, mdsalStrategy);
        assertEquals(DATA_2, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, PATH_2, netconfStrategy);
        assertEquals(DATA_2, normalizedNode);
    }

    @Test
    public void readDataNonConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(netconfService).get(PATH_2);
        NormalizedNode normalizedNode = readData(ContentParam.NONCONFIG, PATH_2, mdsalStrategy);
        assertEquals(DATA_2, normalizedNode);

        normalizedNode = readData(ContentParam.NONCONFIG, PATH_2, netconfStrategy);
        assertEquals(DATA_2, normalizedNode);
    }

    @Test
    public void readContainerDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(netconfService).get(PATH);
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(NODE_IDENTIFIER)
                .withChild(CONTENT_LEAF)
                .withChild(CONTENT_LEAF_2)
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, PATH, mdsalStrategy);
        assertEquals(checkingData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, PATH, netconfStrategy);
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readLeafWithDefaultParameters() {
        final ContainerNode content = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "cont")))
                .withChild(ImmutableNodes.leafNode(QName.create(BASE, "exampleLeaf"), "i am leaf"))
                .build();

        final YangInstanceIdentifier path = YangInstanceIdentifier.builder().node(content.getIdentifier()).build();

        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, path);
        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, path);

        final NormalizedNode normalizedNode = ReadDataTransactionUtil.readData(
                ContentParam.ALL, path, mdsalStrategy, WithDefaultsParam.TRIM, schemaContext);
        assertEquals(content, normalizedNode);
    }

    @Test
    public void readContainerWithDefaultParameters() {
        final QName leafBool = QName.create(BASE, "leafBool");
        final QName containerBool = QName.create(BASE, "containerBool");
        final QName containerInt = QName.create(BASE, "containerInt");
        final QName leafInt = QName.create(BASE, "leafInt");
        final QName exampleList = QName.create(BASE, "exampleList");
        final QName cont = QName.create(BASE, "cont");

        final ContainerNode content = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(cont))
                .withChild(Builders.unkeyedListBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(exampleList))
                    .withChild(Builders.unkeyedListEntryBuilder()
                        .withNodeIdentifier(new NodeIdentifier(exampleList))
                        .withChild(Builders.containerBuilder()
                            .withNodeIdentifier(NodeIdentifier.create(containerBool))
                            .withChild(ImmutableNodes.leafNode(leafBool, true))
                            .build())
                        .addChild(Builders.containerBuilder()
                            .withNodeIdentifier(NodeIdentifier.create(containerInt))
                            .withChild(ImmutableNodes.leafNode(leafInt, 12))
                            .build())
                        .build())
                    .build())
                .build();

        final YangInstanceIdentifier path = YangInstanceIdentifier.builder().node(cont).build();

        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, path);
        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, path);

        final NormalizedNode normalizedNode = ReadDataTransactionUtil.readData(
                ContentParam.ALL, path, mdsalStrategy, WithDefaultsParam.TRIM, schemaContext);

        assertEquals(content, normalizedNode);
    }

    @Test
    public void readLeafInListWithDefaultParameters() {
        final QName leafInList = QName.create(BASE, "leafInList");
        final QName exampleList = QName.create(BASE, "exampleList");
        final QName container = QName.create(BASE, "cont");

        final ContainerNode content = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(container))
                .withChild(Builders.unkeyedListBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(QName.create(BASE, "exampleList")))
                    .withChild(Builders.unkeyedListEntryBuilder()
                        .withNodeIdentifier(new NodeIdentifier(exampleList))
                        .addChild(ImmutableNodes.leafNode(leafInList, "I am leaf in list"))
                        .build())
                    .build())
                .build();

        final YangInstanceIdentifier path = YangInstanceIdentifier.builder().node(container).build();

        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, path);
        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, path);

        final NormalizedNode normalizedNode = ReadDataTransactionUtil.readData(
                ContentParam.ALL, path, mdsalStrategy, WithDefaultsParam.TRIM, schemaContext);
        assertEquals(content, normalizedNode);
    }

    @Test
    public void readContainerDataConfigNoValueOfContentTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(netconfService).get(PATH);
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(NODE_IDENTIFIER)
                .withChild(CONTENT_LEAF)
                .withChild(CONTENT_LEAF_2)
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, PATH, mdsalStrategy);
        assertEquals(checkingData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, PATH, netconfStrategy);
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(netconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(netconfService).getConfig(PATH_3);
        final MapNode checkingData = Builders
                .mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
                .withChild(CHECK_DATA)
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, PATH_3, mdsalStrategy);
        assertEquals(checkingData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, PATH_3, netconfStrategy);
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readOrderedListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(netconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(netconfService)
                .getConfig(PATH_3);
        final MapNode expectedData = Builders.orderedMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(CHECK_DATA)
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, PATH_3,
                mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, PATH_3, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readUnkeyedListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(netconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(netconfService)
                .getConfig(PATH_3);
        final UnkeyedListNode expectedData = Builders.unkeyedListBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(Builders.unkeyedListEntryBuilder()
                        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                        .withChild(UNKEYED_LIST_ENTRY_NODE_1.body().iterator().next())
                        .withChild(UNKEYED_LIST_ENTRY_NODE_2.body().iterator().next()).build()).build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, PATH_3, mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, PATH_3, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readLeafListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(netconfService)
                .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(netconfService)
                .getConfig(LEAF_SET_NODE_PATH);
        final LeafSetNode<String> expectedData = Builders.<String>leafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
                .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                        .addAll(LEAF_SET_NODE_1.body())
                        .addAll(LEAF_SET_NODE_2.body())
                        .build())
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, LEAF_SET_NODE_PATH,
                mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, LEAF_SET_NODE_PATH, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readOrderedLeafListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(netconfService)
                .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(netconfService)
                .getConfig(LEAF_SET_NODE_PATH);
        final LeafSetNode<String> expectedData = Builders.<String>orderedLeafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
                .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                        .addAll(ORDERED_LEAF_SET_NODE_1.body())
                        .addAll(ORDERED_LEAF_SET_NODE_2.body())
                        .build())
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, LEAF_SET_NODE_PATH,
                mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, LEAF_SET_NODE_PATH, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readDataWrongPathOrNoContentTest() {
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(PATH_2);
        NormalizedNode normalizedNode = readData(ContentParam.CONFIG, PATH_2, mdsalStrategy);
        assertNull(normalizedNode);

        normalizedNode = readData(ContentParam.CONFIG, PATH_2, netconfStrategy);
        assertNull(normalizedNode);
    }

    /**
     * Read specific type of data from data store via transaction.
     *
     * @param content        type of data to read (config, state, all)
     * @param strategy       {@link RestconfStrategy} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final YangInstanceIdentifier path, final @NonNull RestconfStrategy strategy) {
        return ReadDataTransactionUtil.readData(content, path, strategy, null, schemaContext);
    }
}
