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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.Futures;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.spi.NetconfRestconfStrategy;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.ErrorInfo;
import org.opendaylight.netconf.databind.ErrorMessage;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.PointParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.JsonResourceBody;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.w3c.dom.DOMException;

@ExtendWith(MockitoExtension.class)
final class NetconfRestconfStrategyTest extends AbstractRestconfStrategyTest {
    private static class TestServerRequest<T> extends MappingServerRequest<T> {
        @NonNullByDefault
        TestServerRequest(final QueryParameters queryParameters, final PrettyPrintParam defaultPrettyPrint) {
            super(null, queryParameters, defaultPrettyPrint, ErrorTagMapping.RFC8040);
        }

        @Override
        protected void onSuccess(final T result) {
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

        @Override
        public QName requestEncoding() {
            return EncodeJson$I.QNAME;
        }
    }

    private final CompletingServerRequest<Empty> emptyRequest = new CompletingServerRequest<>();

    @Mock
    private NetconfDataTreeService netconfService;

    @Override
    RestconfStrategy newDataOperations(final DatabindContext databind) {
        return new NetconfRestconfStrategy(databind, netconfService);
    }

    @Override
    RestconfStrategy testDeleteDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(CONFIGURATION, YangInstanceIdentifier.of());
        return jukeboxDataOperations();
    }

    @Override
    RestconfStrategy testNegativeDeleteDataStrategy() {
        mockLockUnlockDiscard();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(CONFIGURATION, YangInstanceIdentifier.of());
        doReturn(Futures.immediateFailedFuture(new TransactionCommitFailedException(
            "Commit of transaction " + this + " failed", new NetconfDocumentedException("id",
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR)))).when(netconfService).commit();
        return jukeboxDataOperations();
    }

    @Test
    void testDeleteFullList() {
        final var songListWildcardPath = SONG_LIST_PATH.node(NodeIdentifierWithPredicates.of(SONG_QNAME));
        final var songListData = ImmutableNodes.newUserMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(SONG_QNAME))
            .withChild(SONG1).withChild(SONG2).build();
        final var songKeyFields = List.of(YangInstanceIdentifier.of(SONG_INDEX_QNAME));
        mockLockUnlockCommit();
        // data fetched using key field names to minimize amount of data returned
        doReturn(immediateFluentFuture(Optional.of(songListData))).when(netconfService)
            .getConfig(songListWildcardPath, songKeyFields);
        // list elements expected to be deleted one by one
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(CONFIGURATION, SONG1_PATH);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(CONFIGURATION, SONG2_PATH);

        jukeboxDataOperations().deleteData(emptyRequest, jukeboxPath(SONG_LIST_PATH));
        verify(netconfService).getConfig(songListWildcardPath, songKeyFields);
        verify(netconfService).delete(CONFIGURATION, SONG1_PATH);
        verify(netconfService).delete(CONFIGURATION, SONG2_PATH);
        verify(netconfService, never()).delete(CONFIGURATION, SONG_LIST_PATH);
    }

    @Override
    RestconfStrategy testPostContainerDataStrategy() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
        return jukeboxDataOperations();
    }

    @Override
    RestconfStrategy testPostListDataStrategy(final MapEntryNode entryNode, final YangInstanceIdentifier node) {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(eq(CONFIGURATION), eq(PLAYLIST_IID), any(ContainerNode.class), eq(Optional.empty()));
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).create(
            CONFIGURATION, node, entryNode, Optional.empty());
        return jukeboxDataOperations();
    }

    @Override
    RestconfStrategy testPostDataFailStrategy(final DOMException domException) {
        mockLockUnlockDiscard();
        doReturn(immediateFailedFluentFuture(domException)).when(netconfService)
            .create(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
        return jukeboxDataOperations();
    }

    @Override
    RestconfStrategy testPatchContainerDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());
        return jukeboxDataOperations();
    }

    @Override
    RestconfStrategy testPatchLeafDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
        return jukeboxDataOperations();
    }

    @Override
    RestconfStrategy testPatchListDataStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_PLAYLIST, Optional.empty());
        return jukeboxDataOperations();
    }

    @Test
    void testPutCreateContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        verify(netconfService).lock();
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX,
            Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutReplaceContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(netconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX,
            Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutCreateLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        verify(netconfService).getConfig(GAP_IID);
        verify(netconfService).replace(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutReplaceLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(netconfService)
            .getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        verify(netconfService).getConfig(GAP_IID);
        verify(netconfService).replace(CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutCreateListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS,
            Optional.empty());
        assertNotNull(dataPutRequest.getResult());
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
        final var spyOperations = spy(jukeboxDataOperations());
        mockLockUnlockDiscard();

        final var spyTx = spy(jukeboxDataOperations().prepareWriteExecution());
        doReturn(spyTx).when(spyOperations).prepareWriteExecution();
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(SONG3_PATH);
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(spyTx).read(SONG_LIST_PATH);

        // Inserting new song at 3rd position (aka as last element)
        final var request = spy(new TestServerRequest<DataPutResult>(QueryParameters.of(
            // insert new item after last existing item in list
            InsertParam.AFTER, PointParam.forUriValue("example-jukebox:jukebox/playlist=0/song=2")),
            PrettyPrintParam.TRUE));

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

        verify(spyTx).remove(SONG_LIST_PATH);
        verify(spyTx).merge(eq(JUKEBOX_IID), any(ContainerNode.class));
        verify(spyTx).mergeImpl(eq(JUKEBOX_IID), any(ContainerNode.class));
        verify(spyTx).readList(SONG_LIST_PATH);
        verify(spyTx).read(SONG_LIST_PATH);
        verify(spyTx).commit();

        // Counting how many times we insert items in list
        verify(spyTx).replaceImpl(SONG1_PATH, SONG1);
        verify(spyTx).replaceImpl(SONG2_PATH, SONG2);
        verify(spyTx).replaceImpl(SONG3_PATH, ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME, Uint32.valueOf(3)))
            .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "C"))
            .build());
        verifyNoMoreInteractions(spyTx);
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
        final var spyOperations = spy(jukeboxDataOperations());
        mockLockUnlockDiscard();

        final var spyTx = spy(jukeboxDataOperations().prepareWriteExecution());
        doReturn(spyTx).when(spyOperations).prepareWriteExecution();
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(SONG3_PATH);
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(spyTx).read(SONG_LIST_PATH);

        final var request = spy(new TestServerRequest<DataPostResult>(QueryParameters.of(InsertParam.AFTER,
            PointParam.forUriValue("example-jukebox:jukebox/playlist=0/song=2")), PrettyPrintParam.FALSE));

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

        verify(spyTx).remove(SONG_LIST_PATH);
        verify(spyTx).readList(SONG_LIST_PATH);
        verify(spyTx).read(SONG_LIST_PATH);
        verify(spyTx).merge(eq(JUKEBOX_IID), any(ContainerNode.class));
        verify(spyTx).mergeImpl(eq(JUKEBOX_IID), any(ContainerNode.class));
        verify(spyTx).commit();

        // Counting how many times we insert items in list
        verify(spyTx).replaceImpl(SONG1_PATH, SONG1);
        verify(spyTx).replaceImpl(SONG2_PATH, SONG2);
        verify(spyTx).replaceImpl(SONG_LIST_PATH,
            ImmutableNodes.newUserMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(SONG_QNAME))
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME,
                        Uint32.valueOf(3)))
                    .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "C"))
                    .build())
                .build());
        verifyNoMoreInteractions(spyTx);
    }

    @Test
    void testPutReplaceListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(netconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS,
            Optional.empty());
        assertNotNull(dataPutRequest.getResult());
    }

    @Override
    RestconfStrategy testPatchDataReplaceMergeAndRemoveStrategy(final MapNode artistList) {
        mockLockUnlockCommit();

        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).merge(CONFIGURATION,
            ARTIST_IID, artistList, Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .remove(CONFIGURATION, ARTIST_CHILD_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(CONFIGURATION, ARTIST_CHILD_IID, artistList.body().iterator().next(), Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(eq(CONFIGURATION), eq(JUKEBOX_IID), any(ContainerNode.class), eq(Optional.empty()));
        return jukeboxDataOperations();
    }

    @Override
    RestconfStrategy testPatchDataCreateAndDeleteStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(CONFIGURATION, PLAYER_IID, EMPTY_JUKEBOX, Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(CONFIGURATION, GAP_IID);
        return jukeboxDataOperations();
    }

    @Override
    RestconfStrategy testPatchMergePutContainerStrategy() {
        mockLockUnlockCommit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).merge(CONFIGURATION,
            PLAYER_IID, EMPTY_JUKEBOX, Optional.empty());
        return jukeboxDataOperations();
    }

    @Override
    RestconfStrategy deleteNonexistentDataTestStrategy() {
        mockLockUnlockDiscard();
        doReturn(Futures.immediateFailedFuture(
            new TransactionCommitFailedException("Commit of transaction deleteNonexistentData failed",
                new NetconfDocumentedException("Data missing", ErrorType.RPC, ErrorTag.DATA_MISSING,
                    ErrorSeverity.ERROR))))
            .when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(CONFIGURATION, GAP_IID);
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
        assertEquals(new ErrorInfo("Commit of transaction deleteNonexistentData failed"), globalError.info());
        assertEquals(ErrorType.RPC, globalError.type());
        assertEquals(ErrorTag.DATA_MISSING, globalError.tag());
    }

    @Override
    RestconfStrategy readDataConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readAllHavingOnlyConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).get(PATH);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(netconfService).get(PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(PATH_2);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readDataNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(netconfService).get(PATH_2);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readContainerDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(netconfService).get(PATH);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(netconfService).getConfig(PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(netconfService).get(PATH);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(netconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(netconfService).getConfig(PATH_3);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readOrderedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(netconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(netconfService).getConfig(PATH_3);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readUnkeyedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(netconfService).get(PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(netconfService).getConfig(PATH_3);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(netconfService)
            .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(netconfService)
            .getConfig(LEAF_SET_NODE_PATH);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readOrderedLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(netconfService)
            .get(LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(netconfService)
            .getConfig(LEAF_SET_NODE_PATH);
        return mockDataOperations();
    }

    @Override
    RestconfStrategy readDataWrongPathOrNoContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(PATH_2);
        return mockDataOperations();
    }

    private void mockLockUnlock()  {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
    }

    private void mockLockUnlockCommit() {
        mockLockUnlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
    }

    private void mockLockUnlockDiscard() {
        mockLockUnlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).discardChanges();
    }
}
