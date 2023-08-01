/*
 * Copyright (c) 2020 Lumina Networks, Inc. and others. All rights reserved.
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

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
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PlainPatchDataTransactionUtilTest extends AbstractJukeboxTest {
    private static final ContainerNode JUKEBOX_WITH_PLAYLIST = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "MyFavoriteBand-A"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "MyFavoriteBand-A"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description A"))
                .build())
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "MyFavoriteBand-B"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "MyFavoriteBand-B"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description B"))
                .build())
            .build())
        .build();

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

    @Before
    public void before() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
    }

    @Test
    public void testPatchContainerData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).merge(any(), any(),any(),
                any());

        PlainPatchDataTransactionUtil.patchData(JUKEBOX_IID, EMPTY_JUKEBOX,
            new MdsalRestconfStrategy(mockDataBroker), JUKEBOX_SCHEMA);
        verify(readWrite).merge(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);

        PlainPatchDataTransactionUtil.patchData(JUKEBOX_IID, EMPTY_JUKEBOX,
            new NetconfRestconfStrategy(netconfService), JUKEBOX_SCHEMA);
        verify(netconfService).merge(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX,
            Optional.empty());
    }

    @Test
    public void testPatchLeafData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(any(), any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();

        PlainPatchDataTransactionUtil.patchData(GAP_IID, GAP_LEAF, new MdsalRestconfStrategy(mockDataBroker),
            JUKEBOX_SCHEMA);
        verify(readWrite).merge(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);

        PlainPatchDataTransactionUtil.patchData(GAP_IID, GAP_LEAF, new NetconfRestconfStrategy(netconfService),
            JUKEBOX_SCHEMA);
        verify(netconfService).lock();
        verify(netconfService).merge(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
    }

    @Test
    public void testPatchListData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(any(), any(),any(),any());

        PlainPatchDataTransactionUtil.patchData(JUKEBOX_IID, JUKEBOX_WITH_PLAYLIST,
            new MdsalRestconfStrategy(mockDataBroker), JUKEBOX_SCHEMA);
        verify(readWrite).merge(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_PLAYLIST);

        PlainPatchDataTransactionUtil.patchData(JUKEBOX_IID, JUKEBOX_WITH_PLAYLIST,
            new NetconfRestconfStrategy(netconfService), JUKEBOX_SCHEMA);
        verify(netconfService).merge(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_PLAYLIST,
            Optional.empty());
    }
}
