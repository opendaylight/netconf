/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.Futures;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfDataServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PutDataTransactionUtilTest extends AbstractJukeboxTest {
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

    private LeafNode<?> buildLeaf;
    private ContainerNode buildBaseCont;
    private ContainerNode buildBaseContWithList;
    private MapEntryNode buildListEntry;
    private YangInstanceIdentifier iid;
    private YangInstanceIdentifier iid2;
    private YangInstanceIdentifier iid3;

    @Before
    public void setUp() {
        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerQname = QName.create(baseQName, "player");
        final QName leafQname = QName.create(baseQName, "gap");
        final QName listQname = QName.create(baseQName, "playlist");
        final QName listKeyQname = QName.create(baseQName, "name");

        final NodeIdentifierWithPredicates nodeWithKey =
                NodeIdentifierWithPredicates.of(listQname, listKeyQname, "name of band");
        final NodeIdentifierWithPredicates nodeWithKey2 =
                NodeIdentifierWithPredicates.of(listQname, listKeyQname, "name of band 2");

        iid = YangInstanceIdentifier.builder().node(baseQName).node(containerQname).node(leafQname).build();
        iid2 = YangInstanceIdentifier.builder().node(baseQName).build();
        iid3 = YangInstanceIdentifier.builder().node(baseQName).node(listQname).node(nodeWithKey).build();

        buildLeaf = ImmutableNodes.leafNode(leafQname, 0.2);
        buildBaseCont = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(baseQName))
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(containerQname))
                .withChild(buildLeaf)
                .build())
            .build();
        buildListEntry = Builders.mapEntryBuilder()
            .withNodeIdentifier(nodeWithKey)
            .withChild(ImmutableNodes.leafNode(QName.create(baseQName, "name"), "name of band"))
            .withChild(ImmutableNodes.leafNode(QName.create(baseQName, "description"), "band description"))
            .build();
        buildBaseContWithList = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(baseQName))
            .withChild(Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(listQname))
                .withChild(buildListEntry)
                .withChild(Builders.mapEntryBuilder()
                    .withNodeIdentifier(nodeWithKey2)
                    .withChild(ImmutableNodes.leafNode(QName.create(baseQName, "name"), "name of band 2"))
                    .withChild(ImmutableNodes.leafNode(QName.create(baseQName, "description"), "band description 2"))
                    .build())
                .build())
            .build();

        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
    }

    @Test
    public void testValidInputData() {
        RestconfDataServiceImpl.validInputData(true, NormalizedNodePayload.of(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iid), buildLeaf));
    }

    @Test
    public void testValidTopLevelNodeName() {
        RestconfDataServiceImpl.validTopLevelNodeName(iid, NormalizedNodePayload.of(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iid), buildLeaf));
        RestconfDataServiceImpl.validTopLevelNodeName(iid2, NormalizedNodePayload.of(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iid2), buildBaseCont));
    }

    @Test
    public void testValidTopLevelNodeNamePathEmpty() {
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iid);
        final NormalizedNodePayload payload = NormalizedNodePayload.of(iidContext, buildLeaf);

        // FIXME: more asserts
        assertThrows(RestconfDocumentedException.class,
            () -> RestconfDataServiceImpl.validTopLevelNodeName(YangInstanceIdentifier.of(), payload));
    }

    @Test
    public void testValidTopLevelNodeNameWrongTopIdentifier() {
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iid);
        final NormalizedNodePayload payload = NormalizedNodePayload.of(iidContext, buildLeaf);

        // FIXME: more asserts
        assertThrows(RestconfDocumentedException.class,
            () -> RestconfDataServiceImpl.validTopLevelNodeName(iid.getAncestor(1), payload));
    }

    @Test
    public void testValidateListKeysEqualityInPayloadAndUri() {
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iid3);
        final NormalizedNodePayload payload = NormalizedNodePayload.of(iidContext, buildListEntry);
        RestconfDataServiceImpl.validateListKeysEqualityInPayloadAndUri(payload);
    }

    @Test
    public void testPutContainerData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        PutDataTransactionUtil.putData(iid2, buildBaseCont, JUKEBOX_SCHEMA, new MdsalRestconfStrategy(mockDataBroker),
            WriteDataParams.empty());
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont);
    }

    @Test
    public void testPutCreateContainerData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(iid2);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont, Optional.empty());

        PutDataTransactionUtil.putData(iid2, buildBaseCont, JUKEBOX_SCHEMA, new NetconfRestconfStrategy(netconfService),
            WriteDataParams.empty());
        verify(netconfService).lock();
        verify(netconfService).getConfig(iid2);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont, Optional.empty());
    }

    @Test
    public void testPutReplaceContainerData() {
        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(netconfService).getConfig(iid2);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont, Optional.empty());

        PutDataTransactionUtil.putData(iid2, buildBaseCont, JUKEBOX_SCHEMA, new NetconfRestconfStrategy(netconfService),
            WriteDataParams.empty());
        verify(netconfService).getConfig(iid2);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont, Optional.empty());
    }

    @Test
    public void testPutLeafData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, iid);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid, buildLeaf);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        PutDataTransactionUtil.putData(iid, buildLeaf, JUKEBOX_SCHEMA, new MdsalRestconfStrategy(mockDataBroker),
            WriteDataParams.empty());
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, iid);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid, buildLeaf);
    }

    @Test
    public void testPutCreateLeafData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(iid);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, iid, buildLeaf, Optional.empty());

        PutDataTransactionUtil.putData(iid, buildLeaf, JUKEBOX_SCHEMA, new NetconfRestconfStrategy(netconfService),
            WriteDataParams.empty());
        verify(netconfService).getConfig(iid);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, iid, buildLeaf, Optional.empty());
    }

    @Test
    public void testPutReplaceLeafData() {
        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(netconfService).getConfig(iid);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, iid, buildLeaf, Optional.empty());

        PutDataTransactionUtil.putData(iid, buildLeaf, JUKEBOX_SCHEMA, new NetconfRestconfStrategy(netconfService),
            WriteDataParams.empty());
        verify(netconfService).getConfig(iid);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, iid, buildLeaf, Optional.empty());
    }

    @Test
    public void testPutListData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(read).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseContWithList);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        PutDataTransactionUtil.putData(iid2, buildBaseContWithList, JUKEBOX_SCHEMA,
            new MdsalRestconfStrategy(mockDataBroker), WriteDataParams.empty());
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseContWithList);
    }

    @Test
    public void testPutCreateListData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(iid2);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseContWithList, Optional.empty());

        PutDataTransactionUtil.putData(iid2, buildBaseContWithList, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).getConfig(iid2);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseContWithList,
            Optional.empty());
    }

    @Test
    public void testPutReplaceListData() {
        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(netconfService).getConfig(iid2);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseContWithList, Optional.empty());

        PutDataTransactionUtil.putData(iid2, buildBaseContWithList, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).getConfig(iid2);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseContWithList,
            Optional.empty());
    }
}
