/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.Futures;
import java.text.ParseException;
import java.util.HashMap;
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
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonDataPostBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonResourceBody;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
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
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).merge(any(), any(),
            any(), any());
    }

    @Override
    RestconfStrategy newStrategy(final DatabindContext databind) {
        return new NetconfRestconfStrategy(databind, netconfService, null, null, null, null);
    }

    @Override
    RestconfStrategy testDeleteDataStrategy() {
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testNegativeDeleteDataStrategy() {
        doReturn(Futures.immediateFailedFuture(new TransactionCommitFailedException(
            "Commit of transaction " + this + " failed", new NetconfDocumentedException("id",
                ErrorType.RPC, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR)))).when(netconfService).commit();
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPostContainerDataStrategy() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPostListDataStrategy(final MapEntryNode entryNode, final YangInstanceIdentifier node) {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            // FIXME: exact match
            .merge(any(), any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).create(
            LogicalDatastoreType.CONFIGURATION, node, entryNode, Optional.empty());
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPostDataFailStrategy(final DOMException domException) {
        doReturn(immediateFailedFluentFuture(domException)).when(netconfService)
            // FIXME: exact match
            .create(any(), any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).discardChanges();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchContainerDataStrategy() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).merge(any(), any(),any(),
            any());
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchLeafDataStrategy() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(any(), any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchListDataStrategy() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(any(), any(),any(),any());
        return jukeboxStrategy();
    }

    @Test
    public void testPutCreateContainerData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        jukeboxStrategy().putData(JUKEBOX_IID, EMPTY_JUKEBOX, null);
        verify(netconfService).lock();
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX,
            Optional.empty());
    }

    @Test
    public void testPutReplaceContainerData() {
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(netconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        jukeboxStrategy().putData(JUKEBOX_IID, EMPTY_JUKEBOX, null);
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

        jukeboxStrategy().putData(GAP_IID, GAP_LEAF, null);
        verify(netconfService).getConfig(GAP_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
    }

    @Test
    public void testPutReplaceLeafData() {
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(netconfService)
            .getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        jukeboxStrategy().putData(GAP_IID, GAP_LEAF, null);
        verify(netconfService).getConfig(GAP_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
    }

    @Test
    public void testPutCreateListData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        jukeboxStrategy().putData(JUKEBOX_IID, JUKEBOX_WITH_BANDS, null);
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS,
            Optional.empty());
    }

    /**
     * Test for put with insert=after option for last element of the list. Here we're trying to insert new element of
     * the ordered list after last existing element. Test uses list with two items and add third one at the end. After
     * this we check how many times replace transaction was called to know how many items was inserted.
     * @throws ParseException if ApiPath string cannot be parsed
     */
    @Test
    public void testPutDataWithInsertAfterLast() throws ParseException {
        // Spy of jukeboxStrategy will be used later to count how many items was inserted
        final var spyStrategy = spy(jukeboxStrategy());
        final var spyTx = spy(jukeboxStrategy().prepareWriteExecution());
        doReturn(spyTx).when(spyStrategy).prepareWriteExecution();
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(any());

        // For this test we are using
        final var songsList = ImmutableNodes.newUserMapBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(SONG_QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME,
                    Uint32.valueOf(1)))
                .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "A"))
                .build())
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME,
                    Uint32.valueOf(2)))
                .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "B"))
                .build())
            .build();
        doReturn(songsList).when(spyTx).readList(any(YangInstanceIdentifier.class));

        // Creating query params to insert new item after last existing item in list
        final var queryParams = new HashMap<String, String>();
        queryParams.put("insert", "after");
        queryParams.put("point", "example-jukebox:jukebox/playlist=0/song=2");

        // Inserting new song at 3rd position (aka as last element)
        spyStrategy.dataPUT(ApiPath.parse("example-jukebox:jukebox/playlist=0/song=3"),
            new JsonResourceBody(stringInputStream("""
                {
                  "example-jukebox:song" : [
                    {
                       "index": "3",
                       "id" = "C"
                    }
                  ]
                }""")), queryParams);

        // Counting how many times we insert items in list
        verify(spyTx, times(3)).replace(any(), any());
    }

    /**
     * Test for post with insert=after option for last element of the list. Here we're trying to insert new element of
     * the ordered list after last existing element. Test uses list with two items and add third one at the end. After
     * this we check how many times replace transaction was called to know how many items was inserted.
     * @throws ParseException if ApiPath string cannot be parsed
     */
    @Test
    public void testPostDataWithInsertAfterLast() throws ParseException {
        // Spy of jukeboxStrategy will be used later to count how many items was inserted
        final var spyStrategy = spy(jukeboxStrategy());
        final var spyTx = spy(jukeboxStrategy().prepareWriteExecution());
        doReturn(spyTx).when(spyStrategy).prepareWriteExecution();
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(any());

        // For this test we are using
        final var songsList = ImmutableNodes.newUserMapBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(SONG_QNAME))
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(SONG_QNAME,
                        SONG_INDEX_QNAME, Uint32.valueOf(1)))
                    .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "A"))
                    .build())
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(SONG_QNAME,
                        SONG_INDEX_QNAME, Uint32.valueOf(2)))
                    .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "B"))
                    .build())
            .build();
        doReturn(songsList).when(spyTx).readList(any(YangInstanceIdentifier.class));

        // Creating query params to insert new item after last existing item in list
        final var queryParams = new HashMap<String, String>();
        queryParams.put("insert", "after");
        queryParams.put("point", "example-jukebox:jukebox/playlist=0/song=2");

        // Inserting new song at 3rd position (aka as last element)
        spyStrategy.dataPOST(ApiPath.parse("example-jukebox:jukebox/playlist=0/song=3"),
            new JsonDataPostBody(stringInputStream("""
            {
              "id" = "C"
            }""")), queryParams);

        // Counting how many times we insert items in list
        verify(spyTx, times(3)).replace(any(), any());
    }

    @Test
    public void testPutReplaceListData() {
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(netconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        jukeboxStrategy().putData(JUKEBOX_IID, JUKEBOX_WITH_BANDS, null);
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS,
            Optional.empty());
    }

    @Override
    RestconfStrategy testPatchDataReplaceMergeAndRemoveStrategy() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .remove(LogicalDatastoreType.CONFIGURATION, ARTIST_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            // FIXME: exact match
            .replace(any(), any(), any(), any());
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchDataCreateAndDeleteStrategy() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(LogicalDatastoreType.CONFIGURATION, PLAYER_IID, EMPTY_JUKEBOX, Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, CREATE_AND_DELETE_TARGET);
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchMergePutContainerStrategy() {
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy deleteNonexistentDataTestStrategy() {
        doReturn(Futures.immediateFailedFuture(
            new TransactionCommitFailedException("Commit of transaction " + this + " failed",
                new NetconfDocumentedException("id", ErrorType.RPC, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR))))
            .when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, CREATE_AND_DELETE_TARGET);
        return jukeboxStrategy();
    }

    @Override
    void assertTestDeleteNonexistentData(final PatchStatusContext status, final PatchStatusEntity edit) {
        assertNull(edit.getEditErrors());
        final var globalErrors = status.globalErrors();
        assertNotNull(globalErrors);
        assertEquals(1, globalErrors.size());
        final var globalError = globalErrors.get(0);
        assertEquals("Data does not exist", globalError.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, globalError.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, globalError.getErrorTag());
    }

    @Override
    RestconfStrategy readDataConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readAllHavingOnlyConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).get(PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(netconfService).get(PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(PATH_2);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readDataNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(netconfService).get(PATH_2);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readContainerDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(netconfService).get(PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(netconfService).get(PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(netconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(netconfService).getConfig(PATH_3);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readOrderedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(netconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(netconfService).getConfig(PATH_3);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readUnkeyedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(netconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(netconfService).getConfig(PATH_3);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(netconfService)
            .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(netconfService)
            .getConfig(LEAF_SET_NODE_PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readOrderedLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(netconfService)
            .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(netconfService)
            .getConfig(LEAF_SET_NODE_PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readDataWrongPathOrNoContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(PATH_2);
        return mockStrategy();
    }
}
