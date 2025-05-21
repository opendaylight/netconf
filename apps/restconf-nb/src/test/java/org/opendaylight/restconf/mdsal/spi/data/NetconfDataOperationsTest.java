/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.Futures;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDataOperations;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.ErrorInfo;
import org.opendaylight.netconf.databind.ErrorMessage;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.PointParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.mdsal.MdsalServerStrategy;
import org.opendaylight.restconf.server.spi.AbstractServerDataOperations;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.MappingServerRequest;
import org.opendaylight.restconf.server.spi.NotSupportedServerActionOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerMountPointResolver;
import org.opendaylight.restconf.server.spi.NotSupportedServerRpcOperations;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.w3c.dom.DOMException;

@ExtendWith(MockitoExtension.class)
final class NetconfDataOperationsTest extends AbstractServerDataOperationsTest {
    private final CompletingServerRequest<Empty> emptyRequest = new CompletingServerRequest<>();

    @Mock
    private NetconfDataTreeService mockNetconfService;

    NetconfDataOperations jukeboxDataOperations() {
        return new NetconfDataOperations(mockNetconfService);
    }

    @Override
    AbstractServerDataOperations testDeleteDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations testNegativeDeleteDataStrategy() {
        mockLockUnlockDiscard();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
        doReturn(Futures.immediateFailedFuture(new TransactionCommitFailedException(
            "Commit of transaction " + this + " failed", new NetconfDocumentedException("id",
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR)))).when(mockNetconfService).commit();
        return jukeboxDataOperations();
    }

    @Test
    void testDeleteFullList() throws Exception {
        final var songListPath = YangInstanceIdentifier.builder().node(JUKEBOX_QNAME).node(PLAYLIST_QNAME)
            .node(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "playlist"))
            .node(SONG_QNAME).build();
        final var songListWildcardPath = songListPath.node(NodeIdentifierWithPredicates.of(SONG_QNAME));
        final var song1Path = songListPath.node(SONG1.name());
        final var song2Path = songListPath.node(SONG2.name());
        final var songListData = ImmutableNodes.newUserMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(SONG_QNAME))
            .withChild(SONG1).withChild(SONG2).build();
        final var songKeyFields = List.of(YangInstanceIdentifier.of(SONG_INDEX_QNAME));
        mockLockUnlockCommit();
        // data fetched using key field names to minimize amount of data returned
        doReturn(immediateFluentFuture(Optional.of(songListData))).when(mockNetconfService)
            .getConfig(songListWildcardPath, songKeyFields);
        // list elements expected to be deleted one by one
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, song1Path);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, song2Path);

        jukeboxDataOperations().deleteData(emptyRequest, jukeboxPath(songListPath));
        verify(mockNetconfService, timeout(1000)).getConfig(songListWildcardPath, songKeyFields);
        verify(mockNetconfService, timeout(1000)).delete(LogicalDatastoreType.CONFIGURATION, song1Path);
        verify(mockNetconfService, timeout(1000)).delete(LogicalDatastoreType.CONFIGURATION, song2Path);
        verify(mockNetconfService, never()).delete(LogicalDatastoreType.CONFIGURATION, songListPath);
        emptyRequest.getResult();
    }

    @Override
    AbstractServerDataOperations testPostContainerDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .create(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations testPostListDataStrategy(final MapEntryNode entryNode,
            final YangInstanceIdentifier node) {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).create(
            LogicalDatastoreType.CONFIGURATION, node, entryNode, Optional.empty());
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations testPostDataFailStrategy(final DOMException domException) {
        mockLockUnlockDiscard();
        doReturn(immediateFailedFluentFuture(domException)).when(mockNetconfService)
            // FIXME: exact match
            .create(any(), any(), any(), any());
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations testPatchContainerDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).merge(any(), any(),any(),
            any());
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations testPatchLeafDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .merge(any(), any(), any(), any());
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations testPatchListDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .merge(any(), any(),any(),any());
        return jukeboxDataOperations();
    }

    @Test
    void testPutCreateContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        verify(mockNetconfService, timeout(1000)).lock();
        verify(mockNetconfService, timeout(1000)).getConfig(JUKEBOX_IID);
        verify(mockNetconfService, timeout(1000)).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID,
            EMPTY_JUKEBOX, Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutReplaceContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(mockNetconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        verify(mockNetconfService, timeout(1000)).getConfig(JUKEBOX_IID);
        verify(mockNetconfService, timeout(1000)).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID,
            EMPTY_JUKEBOX, Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutCreateLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        verify(mockNetconfService, timeout(1000)).getConfig(GAP_IID);
        verify(mockNetconfService, timeout(1000)).replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF,
            Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutReplaceLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(mockNetconfService)
            .getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        verify(mockNetconfService, timeout(1000)).getConfig(GAP_IID);
        verify(mockNetconfService, timeout(1000)).replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF,
            Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutCreateListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        verify(mockNetconfService, timeout(1000)).getConfig(JUKEBOX_IID);
        verify(mockNetconfService, timeout(1000)).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID,
            JUKEBOX_WITH_BANDS,Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    /**
     * Test for put with insert=after option for last element of the list. Here we're trying to insert new element of
     * the ordered list after last existing element. Test uses list with two items and add third one at the end. After
     * this we check how many times replace transaction was called to know how many items was inserted.
     * @throws ParseException if ApiPath string cannot be parsed
     */
    @Test
    void testPutDataWithInsertAfterLast() throws ParseException {
        // Spy of jukeboxStrategy will be used later to count how many items was inserted
        mockLockUnlockCommit();

        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(any());
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).replace(any(), any(),
            any(), any());
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).remove(any(), any());
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).merge(any(), any(), any(),
            any());

        final var songListPath = YangInstanceIdentifier.builder().node(JUKEBOX_QNAME).node(PLAYLIST_QNAME)
            .node(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "0")).node(SONG_QNAME).build();
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(mockNetconfService)
            .getConfig(songListPath);

        // Inserting new song at 3rd position (aka as last element)
        final var request = spy(new MappingServerRequest<DataPutResult>(null, QueryParameters.of(
            // insert new item after last existing item in list
            InsertParam.AFTER, PointParam.forUriValue("example-jukebox:jukebox/playlist=0/song=2")),
            PrettyPrintParam.TRUE, ErrorTagMapping.RFC8040) {
                @Override
                protected void onSuccess(final DataPutResult result) {
                    // To be verified
                }

                @Override
                protected void onFailure(final HttpStatusCode status, final FormattableBody body) {
                    // To be verified
                }

                @Override
                public TransportSession session() {
                    return null;
                }
        });
        NetconfDataOperations spyOperations = spy(jukeboxDataOperations());
        final var spyStrategy = new MdsalServerStrategy(JUKEBOX_DATABIND, NotSupportedServerMountPointResolver.INSTANCE,
            NotSupportedServerActionOperations.INSTANCE, spyOperations, NotSupportedServerModulesOperations.INSTANCE,
            NotSupportedServerRpcOperations.INSTANCE);

        spyStrategy.dataPUT(request, ApiPath.parse("example-jukebox:jukebox/playlist=0/song=3"),
            new JsonResourceBody(stringInputStream("""
                {
                  "example-jukebox:song" : [
                    {
                       "index": "3",
                       "id" = "C"
                    }
                  ]
                }""")));

        // Counting how many times we insert items in list
        verify(mockNetconfService, timeout(1000).times(3)).replace(any(), any(), any(), any());
        verify(request, timeout(1000)).onSuccess(any());
    }

    /**
     * Test for post with insert=after option for last element of the list. Here we're trying to insert new element of
     * the ordered list after last existing element. Test uses list with two items and add third one at the end. After
     * this we check how many times replace transaction was called to know how many items was inserted.
     * @throws ParseException if ApiPath string cannot be parsed
     */
    @Test
    void testPostDataWithInsertAfterLast() throws ParseException {
        // Spy of jukeboxStrategy will be used later to count how many items was inserted
        mockLockUnlockCommit();

        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(any());
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).replace(any(), any(),
            any(), any());
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).remove(any(), any());

        final var songListPath = YangInstanceIdentifier.builder().node(JUKEBOX_QNAME).node(PLAYLIST_QNAME)
            .node(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "0")).node(SONG_QNAME).build();
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(mockNetconfService)
            .getConfig(songListPath);

        final var request = spy(new MappingServerRequest<DataPostResult>(null, QueryParameters.of(InsertParam.AFTER,
            PointParam.forUriValue("example-jukebox:jukebox/playlist=0/song=2")), PrettyPrintParam.FALSE,
            ErrorTagMapping.RFC8040) {
                @Override
                protected void onSuccess(final DataPostResult result) {
                    // To be verified
                }

                @Override
                protected void onFailure(final HttpStatusCode status, final FormattableBody body) {
                    // To be verified
                }

                @Override
                public TransportSession session() {
                    return null;
                }
        });
        final var spyOperations = jukeboxDataOperations();
        final var spyStrategy = new MdsalServerStrategy(JUKEBOX_DATABIND, NotSupportedServerMountPointResolver.INSTANCE,
            NotSupportedServerActionOperations.INSTANCE, spyOperations, NotSupportedServerModulesOperations.INSTANCE,
            NotSupportedServerRpcOperations.INSTANCE);

        // Inserting new song at 3rd position (aka as last element)
        spyStrategy.dataPOST(request, ApiPath.parse("example-jukebox:jukebox/playlist=0"),
            // insert new item after last existing item in list
            new JsonDataPostBody(stringInputStream("""
                {
                  "example-jukebox:song" : [
                    {
                       "index": "3",
                       "id" = "C"
                    }
                  ]
                }""")));

        // Counting how many times we insert items in list
        verify(mockNetconfService, timeout(1000).times(3)).replace(any(), any(), any(), any());
        verify(request, timeout(1000)).onSuccess(any());
    }

    @Test
    void testPutReplaceListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(mockNetconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        verify(mockNetconfService, timeout(1000)).getConfig(JUKEBOX_IID);
        verify(mockNetconfService, timeout(1000)).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID,
            JUKEBOX_WITH_BANDS,Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Override
    AbstractServerDataOperations testPatchDataReplaceMergeAndRemoveStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).merge(any(), any(),
            any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .remove(LogicalDatastoreType.CONFIGURATION, ARTIST_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            // FIXME: exact match
            .replace(any(), any(), any(), any());
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations testPatchDataCreateAndDeleteStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .create(LogicalDatastoreType.CONFIGURATION, PLAYER_IID, EMPTY_JUKEBOX, Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations testPatchMergePutContainerStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).merge(any(), any(),
            any(), any());
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations deleteNonexistentDataTestStrategy() {
        mockLockUnlockDiscard();
        doReturn(Futures.immediateFailedFuture(
            new TransactionCommitFailedException("Commit of transaction deleteNonexistentData failed",
                new NetconfDocumentedException("Data missing", ErrorType.RPC, ErrorTag.DATA_MISSING,
                    ErrorSeverity.ERROR))))
            .when(mockNetconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        return jukeboxDataOperations();
    }

    @Override
    void assertTestDeleteNonexistentData(final PatchStatusContext status, final PatchStatusEntity edit) {
        assertNull(edit.getEditErrors());
        final var globalErrors = status.globalErrors();
        assertNotNull(globalErrors);
        assertEquals(1, globalErrors.size());
        final var globalError = globalErrors.get(0);
        assertEquals(new ErrorMessage("Data missing"), globalError.message());
        assertEquals(new ErrorInfo("Commit of transaction deleteNonexistentData failed"),
            globalError.info());
        assertEquals(ErrorType.RPC, globalError.type());
        assertEquals(ErrorTag.DATA_MISSING, globalError.tag());
    }

    @Override
    AbstractServerDataOperations readDataConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(mockNetconfService).getConfig(PATH);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readAllHavingOnlyConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(mockNetconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).get(PATH);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(mockNetconfService).get(PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(PATH_2);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readDataNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(mockNetconfService).get(PATH_2);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readContainerDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(mockNetconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(mockNetconfService).get(PATH);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(mockNetconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(mockNetconfService).get(PATH);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(mockNetconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(mockNetconfService).getConfig(PATH_3);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readOrderedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(mockNetconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(mockNetconfService).getConfig(PATH_3);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readUnkeyedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(mockNetconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(mockNetconfService).getConfig(PATH_3);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(mockNetconfService)
            .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(mockNetconfService)
            .getConfig(LEAF_SET_NODE_PATH);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readOrderedLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(mockNetconfService)
            .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(mockNetconfService)
            .getConfig(LEAF_SET_NODE_PATH);
        return jukeboxDataOperations();
    }

    @Override
    AbstractServerDataOperations readDataWrongPathOrNoContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(PATH_2);
        return jukeboxDataOperations();
    }

    @Override
    NormalizedNode readData(final ContentParam content, final DatabindPath.Data path,
            final AbstractServerDataOperations strategy) {
        try {
            return jukeboxDataOperations()
                .readData(path, new DataGetParams(content, DepthParam.max(), null, null))
                .get(2, TimeUnit.SECONDS)
                .orElse(null);
        } catch (TimeoutException | InterruptedException | ExecutionException | RequestException e) {
            throw new AssertionError(e);
        }
    }

    private void mockLockUnlock()  {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).unlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).lock();
    }

    private void mockLockUnlockCommit() {
        mockLockUnlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).commit();
    }

    private void mockLockUnlockDiscard() {
        mockLockUnlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).discardChanges();
    }
}
