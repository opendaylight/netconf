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
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ReadDataTransactionUtilTest {
    private static final TestData DATA = new TestData();
    private static final NodeIdentifier NODE_IDENTIFIER =
        new NodeIdentifier(QName.create("ns", "2016-02-28", "container"));

    private RestconfStrategy mdsalStrategy;
    private RestconfStrategy netconfStrategy;
    @Mock
    private NetconfDataTreeService netconfService;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private EffectiveModelContext mockSchemaContext;
    @Mock
    private DOMDataBroker mockDataBroker;
    private EffectiveModelContext schemaContext;

    @Before
    public void setUp() throws Exception {
        // FIXME: these tests need to be parameterized somehow. The trouble is we need mocking before we invoke
        //        the strategy. This needs some more thought.
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        mdsalStrategy = new MdsalRestconfStrategy(mockDataBroker);
        netconfStrategy = new NetconfRestconfStrategy(netconfService);
        schemaContext = YangParserTestUtils.parseYangFiles(
                TestRestconfUtils.loadFiles("/modules"));
    }

    @Test
    public void readDataConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(netconfService).getConfig(DATA.path);
        NormalizedNode normalizedNode = readData(ContentParam.CONFIG, DATA.path, mdsalStrategy);
        assertEquals(DATA.data3, normalizedNode);

        normalizedNode = readData(ContentParam.CONFIG, DATA.path, netconfStrategy);
        assertEquals(DATA.data3, normalizedNode);
    }

    @Test
    public void readAllHavingOnlyConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(netconfService).getConfig(DATA.path);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).get(DATA.path);
        NormalizedNode normalizedNode = readData(ContentParam.ALL, DATA.path, mdsalStrategy);
        assertEquals(DATA.data3, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, DATA.path, netconfStrategy);
        assertEquals(DATA.data3, normalizedNode);
    }

    @Test
    public void readAllHavingOnlyNonConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path2);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path2);
        doReturn(immediateFluentFuture(Optional.of(DATA.data2))).when(netconfService).get(DATA.path2);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(DATA.path2);
        NormalizedNode normalizedNode = readData(ContentParam.ALL, DATA.path2, mdsalStrategy);
        assertEquals(DATA.data2, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, DATA.path2, netconfStrategy);
        assertEquals(DATA.data2, normalizedNode);
    }

    @Test
    public void readDataNonConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path2);
        doReturn(immediateFluentFuture(Optional.of(DATA.data2))).when(netconfService).get(DATA.path2);
        NormalizedNode normalizedNode = readData(ContentParam.NONCONFIG, DATA.path2, mdsalStrategy);
        assertEquals(DATA.data2, normalizedNode);

        normalizedNode = readData(ContentParam.NONCONFIG, DATA.path2, netconfStrategy);
        assertEquals(DATA.data2, normalizedNode);
    }

    @Test
    public void readContainerDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(netconfService).getConfig(DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data4))).when(netconfService).get(DATA.path);
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(NODE_IDENTIFIER)
                .withChild(DATA.contentLeaf)
                .withChild(DATA.contentLeaf2)
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, DATA.path, mdsalStrategy);
        assertEquals(checkingData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, DATA.path, netconfStrategy);
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readLeafWithDefaultParameters() {
        final ContainerNode content = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(DATA.base, "cont")))
                .withChild(ImmutableNodes.leafNode(QName.create(DATA.base, "exampleLeaf"), "i am leaf"))
                .build();

        final YangInstanceIdentifier path = YangInstanceIdentifier.builder().node(content.name()).build();

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
        final QName leafBool = QName.create(DATA.base, "leafBool");
        final QName containerBool = QName.create(DATA.base, "containerBool");
        final QName containerInt = QName.create(DATA.base, "containerInt");
        final QName leafInt = QName.create(DATA.base, "leafInt");
        final QName exampleList = QName.create(DATA.base, "exampleList");
        final QName cont = QName.create(DATA.base, "cont");

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
        final QName leafInList = QName.create(DATA.base, "leafInList");
        final QName exampleList = QName.create(DATA.base, "exampleList");
        final QName container = QName.create(DATA.base, "cont");

        final ContainerNode content = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(container))
                .withChild(Builders.unkeyedListBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(QName.create(DATA.base, "exampleList")))
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
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(netconfService).getConfig(DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data4))).when(netconfService).get(DATA.path);
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(NODE_IDENTIFIER)
                .withChild(DATA.contentLeaf)
                .withChild(DATA.contentLeaf2)
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, DATA.path, mdsalStrategy);
        assertEquals(checkingData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, DATA.path, netconfStrategy);
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.listData))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.listData2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.listData))).when(netconfService).get(DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.listData2))).when(netconfService).getConfig(DATA.path3);
        final MapNode checkingData = Builders
                .mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
                .withChild(DATA.checkData)
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, DATA.path3, mdsalStrategy);
        assertEquals(checkingData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, DATA.path3, netconfStrategy);
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readOrderedListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedMapNode1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedMapNode2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedMapNode1))).when(netconfService).get(DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedMapNode2))).when(netconfService)
                .getConfig(DATA.path3);
        final MapNode expectedData = Builders.orderedMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(DATA.listQname))
                .withChild(DATA.checkData)
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, DATA.path3,
                mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, DATA.path3, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readUnkeyedListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.unkeyedListNode1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.unkeyedListNode2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.unkeyedListNode1))).when(netconfService).get(DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.unkeyedListNode2))).when(netconfService)
                .getConfig(DATA.path3);
        final UnkeyedListNode expectedData = Builders.unkeyedListBuilder()
                .withNodeIdentifier(new NodeIdentifier(DATA.listQname))
                .withChild(Builders.unkeyedListEntryBuilder()
                        .withNodeIdentifier(new NodeIdentifier(DATA.listQname))
                        .withChild(DATA.unkeyedListEntryNode1.body().iterator().next())
                        .withChild(DATA.unkeyedListEntryNode2.body().iterator().next()).build()).build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, DATA.path3, mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, DATA.path3, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readLeafListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.leafSetNode1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.leafSetNode2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.leafSetNode1))).when(netconfService)
                .get(DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.leafSetNode2))).when(netconfService)
                .getConfig(DATA.leafSetNodePath);
        final LeafSetNode<String> expectedData = Builders.<String>leafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(DATA.leafListQname))
                .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                        .addAll(DATA.leafSetNode1.body())
                        .addAll(DATA.leafSetNode2.body())
                        .build())
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, DATA.leafSetNodePath,
                mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, DATA.leafSetNodePath, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readOrderedLeafListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedLeafSetNode1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedLeafSetNode2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedLeafSetNode1))).when(netconfService)
                .get(DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedLeafSetNode2))).when(netconfService)
                .getConfig(DATA.leafSetNodePath);
        final LeafSetNode<String> expectedData = Builders.<String>orderedLeafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(DATA.leafListQname))
                .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                        .addAll(DATA.orderedLeafSetNode1.body())
                        .addAll(DATA.orderedLeafSetNode2.body())
                        .build())
                .build();
        NormalizedNode normalizedNode = readData(ContentParam.ALL, DATA.leafSetNodePath,
                mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(ContentParam.ALL, DATA.leafSetNodePath, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readDataWrongPathOrNoContentTest() {
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path2);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(DATA.path2);
        NormalizedNode normalizedNode = readData(ContentParam.CONFIG, DATA.path2, mdsalStrategy);
        assertNull(normalizedNode);

        normalizedNode = readData(ContentParam.CONFIG, DATA.path2, netconfStrategy);
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
        return ReadDataTransactionUtil.readData(content, path, strategy, null, mockSchemaContext);
    }
}
