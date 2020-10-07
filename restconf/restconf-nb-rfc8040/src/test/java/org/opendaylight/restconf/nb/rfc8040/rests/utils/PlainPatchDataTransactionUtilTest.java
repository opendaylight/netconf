/*
 * Copyright (c) 2020 Lumina Networks, Inc. and others. All rights reserved.
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;

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
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class PlainPatchDataTransactionUtilTest {
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
    private LeafNode<?> leafGap;
    private ContainerNode jukeboxContainerWithPlayer;
    private ContainerNode jukeboxContainerWithPlaylist;
    private EffectiveModelContext schema;
    private DataSchemaNode schemaNodeForGap;
    private YangInstanceIdentifier iidGap;
    private DataSchemaNode schemaNodeForJukebox;
    private YangInstanceIdentifier iidJukebox;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.schema = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));

        final QName qnJukebox = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName qnPlayer = QName.create(qnJukebox, "player");
        final QName qnGap = QName.create(qnJukebox, "gap");
        final QName qnPlaylist = QName.create(qnJukebox, "playlist");
        final QName qnPlaylistKey = QName.create(qnJukebox, "name");

        final NodeIdentifierWithPredicates nidBandA =
                NodeIdentifierWithPredicates.of(qnPlaylist, qnPlaylistKey, "MyFavoriteBand-A");
        final NodeIdentifierWithPredicates nidBandB =
                NodeIdentifierWithPredicates.of(qnPlaylist, qnPlaylistKey, "MyFavoriteBand-B");

        this.iidGap = YangInstanceIdentifier.builder()
                .node(qnJukebox)
                .node(qnPlayer)
                .node(qnGap)
                .build();
        this.schemaNodeForGap = DataSchemaContextTree.from(this.schema).findChild(this.iidGap).orElseThrow()
                .getDataSchemaNode();

        this.iidJukebox = YangInstanceIdentifier.builder()
                .node(qnJukebox)
                .build();
        this.schemaNodeForJukebox = DataSchemaContextTree.from(this.schema)
                .findChild(this.iidJukebox).orElseThrow().getDataSchemaNode();

        this.leafGap = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(qnGap))
                .withValue(0.2)
                .build();
        final ContainerNode playerContainer = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(qnPlayer))
                .withChild(this.leafGap)
                .build();
        this.jukeboxContainerWithPlayer = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(qnJukebox))
                .withChild(playerContainer)
                .build();

        // ----------

        final LeafNode<Object> leafBandA = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(qnJukebox, "name")))
                .withValue("MyFavoriteBand-A")
                .build();
        final LeafNode<Object> leafDescriptionA = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(qnJukebox, "description")))
                .withValue("band description A")
                .build();
        final MapEntryNode entryBandA = Builders.mapEntryBuilder()
                .withNodeIdentifier(nidBandA)
                .withChild(leafBandA)
                .withChild(leafDescriptionA)
                .build();

        final LeafNode<Object> leafBandB = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(qnJukebox, "name")))
                .withValue("MyFavoriteBand-B")
                .build();
        final LeafNode<Object> leafDescriptionB = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(qnJukebox, "description")))
                .withValue("band description B")
                .build();
        final MapEntryNode entryBandB = Builders.mapEntryBuilder()
                .withNodeIdentifier(nidBandB)
                .withChild(leafBandB)
                .withChild(leafDescriptionB)
                .build();

        final MapNode listBands = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(qnPlaylist))
                .withChild(entryBandA)
                .withChild(entryBandB)
                .build();
        this.jukeboxContainerWithPlaylist = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(qnJukebox))
                .withChild(listBands)
                .build();

        Mockito.doReturn(transactionChain).when(mockDataBroker).createTransactionChain(Mockito.any());
        transactionChainHandler = new TransactionChainHandler(mockDataBroker);
    }

    @Test
    public void testPatchContainerData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iidJukebox, this.schemaNodeForJukebox, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.jukeboxContainerWithPlayer);

        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.write).when(this.transactionChain).newWriteOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidJukebox);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
        doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        PlainPatchDataTransactionUtil.patchData(payload, new MdsalRestconfStrategy(transactionChainHandler),
                this.schema);
        verify(this.readWrite).merge(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());

        PlainPatchDataTransactionUtil.patchData(payload, new NetconfRestconfStrategy(netconfService),
                this.schema);
        verify(this.netconfService).merge(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(), Optional.empty());
    }

    @Test
    public void testPatchLeafData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iidGap, this.schemaNodeForGap, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.leafGap);

        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.write).when(this.transactionChain).newWriteOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidGap);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
        doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        PlainPatchDataTransactionUtil.patchData(payload, new MdsalRestconfStrategy(transactionChainHandler),
                this.schema);
        verify(this.readWrite).merge(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());

        PlainPatchDataTransactionUtil.patchData(payload, new NetconfRestconfStrategy(netconfService),
                this.schema);
        verify(this.netconfService).merge(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(), Optional.empty());
    }

    @Test
    public void testPatchListData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iidJukebox, this.schemaNodeForJukebox, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.jukeboxContainerWithPlaylist);

        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.write).when(this.transactionChain).newWriteOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidJukebox);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
        doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        PlainPatchDataTransactionUtil.patchData(payload, new MdsalRestconfStrategy(transactionChainHandler),
                this.schema);
        verify(this.readWrite).merge(LogicalDatastoreType.CONFIGURATION, this.iidJukebox, payload.getData());

        PlainPatchDataTransactionUtil.patchData(payload, new NetconfRestconfStrategy(netconfService),
                this.schema);
        verify(this.netconfService).merge(LogicalDatastoreType.CONFIGURATION, this.iidJukebox, payload.getData(),
                Optional.empty());
    }
}
