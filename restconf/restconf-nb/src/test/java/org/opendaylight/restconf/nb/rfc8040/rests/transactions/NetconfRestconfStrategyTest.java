/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.Futures;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PutDataTransactionUtil;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.w3c.dom.DOMException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public final class NetconfRestconfStrategyTest extends AbstractRestconfStrategyTest {
    @Mock
    private NetconfDataTreeService netconfService;

    @Before
    public void before() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).discardChanges();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
    }

    @Override
    RestconfStrategy testDeleteDataStrategy() {
        return new NetconfRestconfStrategy(netconfService);
    }

    @Override
    RestconfStrategy testNegativeDeleteDataStrategy() {
        doReturn(Futures.immediateFailedFuture(new TransactionCommitFailedException(
            "Commit of transaction " + this + " failed", new NetconfDocumentedException("id",
                ErrorType.RPC, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR)))).when(netconfService).commit();
        return new NetconfRestconfStrategy(netconfService);
    }

    @Override
    RestconfStrategy testPostContainerDataStrategy() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
        return new NetconfRestconfStrategy(netconfService);
    }

    @Override
    RestconfStrategy testPostListDataStrategy(final MapEntryNode entryNode, final YangInstanceIdentifier node) {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(any(), any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).create(
            LogicalDatastoreType.CONFIGURATION, node, entryNode, Optional.empty());
        return new NetconfRestconfStrategy(netconfService);
    }

    @Override
    RestconfStrategy testPostDataFailStrategy(final DOMException domException) {
        doReturn(immediateFailedFluentFuture(domException)).when(netconfService)
            .create(any(), any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).discardChanges();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
        return new NetconfRestconfStrategy(netconfService);
    }

    @Test
    public void testPutCreateContainerData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        PutDataTransactionUtil.putData(JUKEBOX_IID, EMPTY_JUKEBOX, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).lock();
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX,
            Optional.empty());
    }

    @Test
    public void testPutReplaceContainerData() {
        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(netconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        PutDataTransactionUtil.putData(JUKEBOX_IID, EMPTY_JUKEBOX, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX,
            Optional.empty());
    }

    @Test
    public void testPutCreateLeafData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        PutDataTransactionUtil.putData(GAP_IID, GAP_LEAF, JUKEBOX_SCHEMA, new NetconfRestconfStrategy(netconfService),
            WriteDataParams.empty());
        verify(netconfService).getConfig(GAP_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
    }

    @Test
    public void testPutReplaceLeafData() {
        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(netconfService)
            .getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        PutDataTransactionUtil.putData(GAP_IID, GAP_LEAF, JUKEBOX_SCHEMA, new NetconfRestconfStrategy(netconfService),
            WriteDataParams.empty());
        verify(netconfService).getConfig(GAP_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
    }

    @Test
    public void testPutCreateListData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        PutDataTransactionUtil.putData(JUKEBOX_IID, JUKEBOX_WITH_BANDS, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS,
            Optional.empty());
    }

    @Test
    public void testPutReplaceListData() {
        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(netconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        PutDataTransactionUtil.putData(JUKEBOX_IID, JUKEBOX_WITH_BANDS, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS,
            Optional.empty());
    }
}
