/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class PutDataTransactionUtilTest {
    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";
    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private DOMDataTreeWriteTransaction write;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    private TransactionChainHandler transactionChainHandler;
    private LeafNode<?> buildLeaf;
    private ContainerNode buildBaseCont;
    private ContainerNode buildBaseContWithList;
    private MapEntryNode buildListEntry;
    private EffectiveModelContext schema;
    private DataSchemaNode schemaNode;
    private YangInstanceIdentifier iid;
    private DataSchemaNode schemaNode2;
    private YangInstanceIdentifier iid2;
    private DataSchemaNode schemaNode3;
    private YangInstanceIdentifier iid3;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.schema =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));

        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerQname = QName.create(baseQName, "player");
        final QName leafQname = QName.create(baseQName, "gap");
        final QName listQname = QName.create(baseQName, "playlist");
        final QName listKeyQname = QName.create(baseQName, "name");

        final NodeIdentifierWithPredicates nodeWithKey =
                NodeIdentifierWithPredicates.of(listQname, listKeyQname, "name of band");
        final NodeIdentifierWithPredicates nodeWithKey2 =
                NodeIdentifierWithPredicates.of(listQname, listKeyQname, "name of band 2");

        final DataSchemaContextTree tree = DataSchemaContextTree.from(this.schema);
        this.iid = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerQname)
                .node(leafQname)
                .build();
        this.schemaNode = tree.findChild(this.iid).orElseThrow().getDataSchemaNode();

        this.iid2 = YangInstanceIdentifier.builder()
                .node(baseQName)
                .build();
        this.schemaNode2 = tree.findChild(this.iid2).orElseThrow().getDataSchemaNode();

        this.iid3 = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(listQname)
                .node(nodeWithKey)
                .build();
        this.schemaNode3 = tree.findChild(this.iid3).orElseThrow().getDataSchemaNode();

        this.buildLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(leafQname))
                .withValue(0.2)
                .build();
        final ContainerNode buildPlayerCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(containerQname))
                .withChild(this.buildLeaf)
                .build();
        this.buildBaseCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(baseQName))
                .withChild(buildPlayerCont)
                .build();
        final LeafNode<Object> content = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of band")
                .build();
        final LeafNode<Object> content2 = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "description")))
                .withValue("band description")
                .build();
        this.buildListEntry = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content)
                .withChild(content2)
                .build();
        final LeafNode<Object> content3 = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of band 2")
                .build();
        final LeafNode<Object> content4 = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "description")))
                .withValue("band description 2")
                .build();
        final MapEntryNode buildListEntry2 = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey2)
                .withChild(content3)
                .withChild(content4)
                .build();
        final MapNode buildList = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(listQname))
                .withChild(this.buildListEntry)
                .withChild(buildListEntry2)
                .build();
        this.buildBaseContWithList = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(baseQName))
                .withChild(buildList)
                .build();

        Mockito.doReturn(transactionChain).when(mockDataBroker).createTransactionChain(Mockito.any());
        transactionChainHandler = new TransactionChainHandler(mockDataBroker);
    }

    @Test
    public void testValidInputData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);
        PutDataTransactionUtil.validInputData(iidContext.getSchemaNode(), payload);
    }

    @Test
    public void testValidTopLevelNodeName() {
        InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);
        PutDataTransactionUtil.validTopLevelNodeName(iidContext.getInstanceIdentifier(), payload);

        iidContext = new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);
        PutDataTransactionUtil.validTopLevelNodeName(iidContext.getInstanceIdentifier(), payload);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testValidTopLevelNodeNamePathEmpty() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);
        PutDataTransactionUtil.validTopLevelNodeName(YangInstanceIdentifier.empty(), payload);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testValidTopLevelNodeNameWrongTopIdentifier() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);
        PutDataTransactionUtil.validTopLevelNodeName(this.iid.getAncestor(1), payload);
    }

    @Test
    public void testValidateListKeysEqualityInPayloadAndUri() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid3, this.schemaNode3, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildListEntry);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);
    }

    @Test
    public void testPutContainerData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.write).when(this.transactionChain).newWriteOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iid2);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
        doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();

        PutDataTransactionUtil.putData(payload, this.schema, new MdsalRestconfStrategy(transactionChainHandler),
                null, null);
        verify(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
    }

    @Test
    public void testPutCreateContainerData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService).getConfig(this.iid2);
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        PutDataTransactionUtil.putData(payload, this.schema, new NetconfRestconfStrategy(netconfService),
                null, null);
        verify(this.netconfService).getConfig(payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(this.netconfService).create(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(), Optional.empty());
    }

    @Test
    public void testPutReplaceContainerData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class))))
                .when(this.netconfService).getConfig(this.iid2);
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        PutDataTransactionUtil.putData(payload, this.schema, new NetconfRestconfStrategy(netconfService),
                null, null);
        verify(this.netconfService).getConfig(payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(this.netconfService).replace(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(), Optional.empty());
    }

    @Test
    public void testPutLeafData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);

        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.write).when(this.transactionChain).newWriteOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iid);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
        doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();

        PutDataTransactionUtil.putData(payload, this.schema, new MdsalRestconfStrategy(transactionChainHandler),
                null, null);
        verify(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
    }

    @Test
    public void testPutCreateLeafData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);

        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService).getConfig(this.iid);
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        PutDataTransactionUtil.putData(payload, this.schema, new NetconfRestconfStrategy(netconfService),
                null, null);
        verify(this.netconfService).getConfig(payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(this.netconfService).create(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(), Optional.empty());
    }

    @Test
    public void testPutReplaceLeafData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);

        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class))))
                .when(this.netconfService).getConfig(this.iid);
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        PutDataTransactionUtil.putData(payload, this.schema,
                new NetconfRestconfStrategy(netconfService), null, null);
        verify(this.netconfService).getConfig(payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(this.netconfService).replace(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(), Optional.empty());
    }

    @Test
    public void testPutListData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseContWithList);

        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.write).when(this.transactionChain).newWriteOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iid2);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
        doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();
        PutDataTransactionUtil.putData(payload, this.schema, new MdsalRestconfStrategy(transactionChainHandler),
                null, null);
        verify(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iid2);
        verify(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, this.iid2, payload.getData());
    }

    @Test
    public void testPutCreateListData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseContWithList);

        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService)
                .getConfig(this.iid2);
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        PutDataTransactionUtil.putData(payload, this.schema, new NetconfRestconfStrategy(netconfService),
                null, null);
        verify(this.netconfService).getConfig(this.iid2);
        verify(this.netconfService).create(LogicalDatastoreType.CONFIGURATION, this.iid2,
                payload.getData(), Optional.empty());
    }

    @Test
    public void testPutReplaceListData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseContWithList);

        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(this.netconfService)
                .getConfig(this.iid2);
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        PutDataTransactionUtil.putData(payload, this.schema,
                new NetconfRestconfStrategy(netconfService), null, null);
        verify(this.netconfService).getConfig(this.iid2);
        verify(this.netconfService).replace(LogicalDatastoreType.CONFIGURATION, this.iid2,
                payload.getData(), Optional.empty());
    }
}
