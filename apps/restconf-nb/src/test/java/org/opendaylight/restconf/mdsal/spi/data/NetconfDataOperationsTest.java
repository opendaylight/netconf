/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.Futures;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.spi.DataOperationImpl;
import org.opendaylight.netconf.client.mdsal.spi.DataOperationService;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDataOperations;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.ErrorInfo;
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
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.PatchEntity;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.mdsal.MdsalServerStrategy;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.MappingServerRequest;
import org.opendaylight.restconf.server.spi.NotSupportedServerActionOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerMountPointResolver;
import org.opendaylight.restconf.server.spi.NotSupportedServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.w3c.dom.DOMException;

@ExtendWith(MockitoExtension.class)
final class NetconfDataOperationsTest extends AbstractServerDataOperationsTest {
    private final CompletingServerRequest<Empty> emptyRequest = new CompletingServerRequest<>();

    @Mock
    private NetconfDataTreeService mockNetconfService;

    private DataOperationService operationService;
    private NetconfDataOperations netconfData;

    @BeforeEach
    public void beforeEach() {
        operationService = new DataOperationImpl(mockNetconfService);
        netconfData = new NetconfDataOperations(operationService);
    }

    @Override
    ServerDataOperations testDeleteDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(CONFIGURATION, YangInstanceIdentifier.of());
        return netconfData;
    }

    @Override
    ServerDataOperations testNegativeDeleteDataStrategy() {
        mockLockUnlockDiscard();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(CONFIGURATION, YangInstanceIdentifier.of());
        doReturn(Futures.immediateFailedFuture(new NetconfDocumentedException("Data missing",
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR))).when(mockNetconfService).commit();
        return netconfData;
    }

    @Test
    void testDeleteFullList() throws Exception {
        final var songListWildcardPath = SONG_LIST_PATH.node(NodeIdentifierWithPredicates.of(SONG_QNAME));
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
            .delete(CONFIGURATION, SONG1_PATH);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(CONFIGURATION, SONG2_PATH);

        netconfData.deleteData(emptyRequest, jukeboxPath(SONG_LIST_PATH));
        emptyRequest.getResult();
        verify(mockNetconfService).getConfig(songListWildcardPath, songKeyFields);
        verify(mockNetconfService).delete(CONFIGURATION, SONG1_PATH);
        verify(mockNetconfService).delete(CONFIGURATION, SONG2_PATH);
        verify(mockNetconfService, never()).delete(CONFIGURATION, SONG_LIST_PATH);
    }

    @Override
    ServerDataOperations testPostContainerDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .create(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
        return netconfData;
    }

    @Override
    ServerDataOperations testPostListDataStrategy(final MapEntryNode entryNode,
            final YangInstanceIdentifier node) {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).create(
            LogicalDatastoreType.CONFIGURATION, node, entryNode, Optional.empty());
        return netconfData;
    }

    @Override
    ServerDataOperations testPostDataFailStrategy(final DOMException domException) {
        mockLockUnlockDiscard();
        doReturn(immediateFailedFluentFuture(domException)).when(mockNetconfService)
            .create(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchContainerDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .merge(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchLeafDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .merge(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchListDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .merge(CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_PLAYLIST, Optional.empty());
        return netconfData;
    }

    @Test
    void testPutCreateContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        assertNotNull(dataPutRequest.getResult());
        verify(mockNetconfService).lock();
        verify(mockNetconfService).getConfig(JUKEBOX_IID);
        verify(mockNetconfService).replace(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
    }

    @Test
    void testPutReplaceContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(mockNetconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        assertNotNull(dataPutRequest.getResult());
        verify(mockNetconfService).getConfig(JUKEBOX_IID);
        verify(mockNetconfService).replace(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
    }

    @Test
    void testPutCreateLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        netconfData.putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        assertNotNull(dataPutRequest.getResult());
        verify(mockNetconfService).getConfig(GAP_IID);
        verify(mockNetconfService).replace(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
    }

    @Test
    void testPutReplaceLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(mockNetconfService)
            .getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        netconfData.putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        assertNotNull(dataPutRequest.getResult());
        verify(mockNetconfService).getConfig(GAP_IID);
        verify(mockNetconfService).replace(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
    }

    @Test
    void testPutCreateListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        assertNotNull(dataPutRequest.getResult());
        verify(mockNetconfService).getConfig(JUKEBOX_IID);
        verify(mockNetconfService).replace(CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS,Optional.empty());
    }

    /**
     * Test for put with insert=after option for last element of the list. Here we're trying to insert new element of
     * the ordered list after last existing element. Test uses list with two items and add third one at the end. After
     * this we check how many times replace transaction was called to know how many items was inserted.
     * @throws ParseException if ApiPath string cannot be parsed
     */
    @Test
    void testPutDataWithInsertAfterLast() throws Exception {
        // Spy of jukeboxStrategy will be used later to count how many items was inserted
        mockLockUnlockCommit();

        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(SONG3_PATH);
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).replace(eq(CONFIGURATION),
            any(YangInstanceIdentifier.class), any(MapEntryNode.class), eq(Optional.empty()));
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).remove(CONFIGURATION,
            SONG1_PATH);
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).remove(CONFIGURATION,
            SONG2_PATH);
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(mockNetconfService)
            .getConfig(SONG_LIST_PATH);

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
        final var strategy = new MdsalServerStrategy(JUKEBOX_DATABIND, NotSupportedServerMountPointResolver.INSTANCE,
            NotSupportedServerActionOperations.INSTANCE, netconfData, NotSupportedServerModulesOperations.INSTANCE,
            NotSupportedServerRpcOperations.INSTANCE);

        strategy.dataPUT(request, ApiPath.parse("example-jukebox:jukebox/playlist=0/song=3"),
            new JsonResourceBody(stringInputStream("""
                {
                  "example-jukebox:song" : [
                    {
                       "index": "3",
                       "id" = "C"
                    }
                  ]
                }""")));
        verify(request, timeout(1000)).onSuccess(any());

        verify(mockNetconfService).remove(CONFIGURATION, SONG1_PATH);
        verify(mockNetconfService).remove(CONFIGURATION, SONG2_PATH);
        verify(mockNetconfService).getConfig(SONG_LIST_PATH);
        verify(mockNetconfService).lock();
        verify(mockNetconfService).unlock();
        verify(mockNetconfService).commit();

        // Counting how many times we insert items in list
        verify(mockNetconfService).replace(CONFIGURATION, SONG1_PATH, SONG1, Optional.empty());
        verify(mockNetconfService).replace(CONFIGURATION, SONG2_PATH, SONG2, Optional.empty());
        verify(mockNetconfService).replace(CONFIGURATION, SONG3_PATH, ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME, Uint32.valueOf(3)))
            .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "C"))
            .build(), Optional.empty());
        verifyNoMoreInteractions(mockNetconfService);
    }

    /**
     * Test for post with insert=after option for last element of the list. Here we're trying to insert new element of
     * the ordered list after last existing element. Test uses list with two items and add third one at the end. After
     * this we check how many times replace transaction was called to know how many items was inserted.
     * @throws ParseException if ApiPath string cannot be parsed
     */
    @Test
    void testPostDataWithInsertAfterLast() throws Exception {
        // Spy of jukeboxStrategy will be used later to count how many items was inserted
        mockLockUnlockCommit();

        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(SONG3_PATH);
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).replace(eq(CONFIGURATION),
            any(YangInstanceIdentifier.class), any(MapEntryNode.class), eq(Optional.empty()));
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).remove(CONFIGURATION,
            SONG1_PATH);
        doReturn(immediateFluentFuture(new DefaultDOMRpcResult())).when(mockNetconfService).remove(CONFIGURATION,
            SONG2_PATH);

        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(mockNetconfService)
            .getConfig(SONG_LIST_PATH);

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
        final var strategy = new MdsalServerStrategy(JUKEBOX_DATABIND, NotSupportedServerMountPointResolver.INSTANCE,
            NotSupportedServerActionOperations.INSTANCE, netconfData, NotSupportedServerModulesOperations.INSTANCE,
            NotSupportedServerRpcOperations.INSTANCE);

        // Inserting new song at 3rd position (aka as last element)
        strategy.dataPOST(request, ApiPath.parse("example-jukebox:jukebox/playlist=0"),
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
        verify(request, timeout(1000)).onSuccess(any());

        verify(mockNetconfService).remove(CONFIGURATION, SONG1_PATH);
        verify(mockNetconfService).remove(CONFIGURATION, SONG2_PATH);
        verify(mockNetconfService).getConfig(SONG_LIST_PATH);
        verify(mockNetconfService).lock();
        verify(mockNetconfService).commit();
        verify(mockNetconfService).unlock();

        // Counting how many times we insert items in list
        verify(mockNetconfService).replace(CONFIGURATION, SONG1_PATH, SONG1, Optional.empty());
        verify(mockNetconfService).replace(CONFIGURATION, SONG2_PATH, SONG2, Optional.empty());
        verify(mockNetconfService).replace(CONFIGURATION, SONG3_PATH,
            ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME, Uint32.valueOf(3)))
                .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "C"))
                .build(),Optional.empty());
        verifyNoMoreInteractions(mockNetconfService);
    }

    @Test
    void testPutReplaceListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(mockNetconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        verify(mockNetconfService, timeout(1000)).getConfig(JUKEBOX_IID);
        verify(mockNetconfService, timeout(1000)).replace(CONFIGURATION, JUKEBOX_IID,
            JUKEBOX_WITH_BANDS,Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testLockOperationException() throws Exception {
        // Prepare environment.
        final var rpcError = RpcResultBuilder.newError(ErrorType.PROTOCOL, ErrorTag.OPERATION_FAILED,
            "Requested resource already lockedUser callback failed.", null, "", null);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcError))).when(mockNetconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).unlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).discardChanges();

        // Execute yang-patch with failing lock operation.
        final var patchContext = new PatchContext("patchCD", List.of(new PatchEntity("edit1", Edit.Operation.Delete,
            GAP_PATH)));
        final var completingServerRequest = new CompletingServerRequest<DataYangPatchResult>();
        netconfData.patchData(completingServerRequest, new Data(GAP_PATH.databind()), patchContext);
        final var status = completingServerRequest.getResult().status();

        // Verify correct exception output.
        assertFalse(status.ok());
        final var globalErrors = status.globalErrors();
        assertNotNull(globalErrors);
        final var serverError = globalErrors.getFirst();
        assertNotNull(serverError);
        assertEquals(ErrorTag.OPERATION_FAILED, serverError.tag());
        assertEquals("RPC during tx failed. Requested resource already lockedUser callback failed. ",
            serverError.info().elementBody());
    }

    @Override
    ServerDataOperations testPatchDataReplaceMergeAndRemoveStrategy(final MapNode artistList) {
        mockLockUnlockCommit();

        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).merge(CONFIGURATION,
            ARTIST_IID, artistList, Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .remove(CONFIGURATION, ARTIST_CHILD_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(CONFIGURATION, ARTIST_CHILD_IID, artistList.body().iterator().next(), Optional.empty());
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchDataCreateAndDeleteStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .create(CONFIGURATION, PLAYER_IID, EMPTY_JUKEBOX, Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(CONFIGURATION, GAP_IID);
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchWithDataExistExceptionStrategy() {
        mockLockUnlockDiscard();
        final var rpcError = RpcResultBuilder.newError(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
            "Data already exists", null, "", null);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .replace(eq(CONFIGURATION), eq(ARTIST_CHILD_IID), any(MapEntryNode.class), eq(Optional.empty()));
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcError))).when(mockNetconfService)
            .create(LogicalDatastoreType.CONFIGURATION, PLAYER_IID, EMPTY_JUKEBOX, Optional.empty());
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchMergePutContainerStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).merge(CONFIGURATION,
            PLAYER_IID, EMPTY_JUKEBOX, Optional.empty());
        return netconfData;
    }

    @Override
    ServerDataOperations deleteNonexistentDataTestStrategy() {
        mockLockUnlockDiscard();
        doReturn(Futures.immediateFailedFuture(
            new NetconfDocumentedException("Data missing", ErrorType.RPC, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR)))
            .when(mockNetconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .delete(CONFIGURATION, GAP_IID);
        return netconfData;
    }

    @Override
    void assertTestDeleteNonexistentData(final PatchStatusContext status, final PatchStatusEntity edit) {
        assertNull(edit.getEditErrors());
        final var globalErrors = status.globalErrors();
        assertNotNull(globalErrors);
        assertEquals(1, globalErrors.size());
        final var globalError = globalErrors.get(0);
        assertEquals(new ErrorInfo("Data missing"), globalError.info());
        assertEquals(ErrorType.RPC, globalError.type());
        assertEquals(ErrorTag.DATA_MISSING, globalError.tag());
    }

    @Override
    ServerDataOperations readDataConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(mockNetconfService).getConfig(PATH);
        return netconfData;
    }

    @Override
    ServerDataOperations readAllHavingOnlyConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(mockNetconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).get(PATH);
        return netconfData;
    }

    @Override
    ServerDataOperations readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(mockNetconfService).get(PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(PATH_2);
        return netconfData;
    }

    @Override
    ServerDataOperations readDataNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(mockNetconfService).get(PATH_2);
        return netconfData;
    }

    @Override
    ServerDataOperations readContainerDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(mockNetconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(mockNetconfService).get(PATH);
        return netconfData;
    }

    @Override
    ServerDataOperations readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(mockNetconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(mockNetconfService).get(PATH);
        return netconfData;
    }

    @Override
    ServerDataOperations readListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(mockNetconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(mockNetconfService).getConfig(PATH_3);
        return netconfData;
    }

    @Override
    ServerDataOperations readOrderedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(mockNetconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(mockNetconfService).getConfig(PATH_3);
        return netconfData;
    }

    @Override
    ServerDataOperations readUnkeyedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(mockNetconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(mockNetconfService).getConfig(PATH_3);
        return netconfData;
    }

    @Override
    ServerDataOperations readLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(mockNetconfService)
            .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(mockNetconfService)
            .getConfig(LEAF_SET_NODE_PATH);
        return netconfData;
    }

    @Override
    ServerDataOperations readOrderedLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(mockNetconfService)
            .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(mockNetconfService)
            .getConfig(LEAF_SET_NODE_PATH);
        return netconfData;
    }

    @Override
    ServerDataOperations readDataWrongPathOrNoContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.empty())).when(mockNetconfService).getConfig(PATH_2);
        return netconfData;
    }

    @Override
    void assertReadDataWrongPathOrNoContentTest(final Supplier<NormalizedNode> readResult) {
        assertNull(readResult.get());
    }

    @Override
    NormalizedNode readData(final ContentParam content, final Data path,
            final ServerDataOperations strategy) {
        try {
            return operationService
                .getData(path, new DataGetParams(content, DepthParam.max(), null, null))
                .get(2, TimeUnit.SECONDS)
                .orElse(null);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
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
