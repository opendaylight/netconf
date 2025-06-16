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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.netconf.api.EffectiveOperation.CREATE;
import static org.opendaylight.netconf.api.EffectiveOperation.DELETE;
import static org.opendaylight.netconf.api.EffectiveOperation.REPLACE;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.netconf.client.mdsal.spi.AbstractDataStore;
import org.opendaylight.netconf.client.mdsal.spi.DataOperationImpl;
import org.opendaylight.netconf.client.mdsal.spi.DataOperationService;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDataOperations;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.ErrorInfo;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.w3c.dom.DOMException;

@ExtendWith(MockitoExtension.class)
final class NetconfDataOperationsTest extends AbstractServerDataOperationsTest {
    private final CompletingServerRequest<Empty> emptyRequest = new CompletingServerRequest<>();

    @Mock
    private NetconfBaseOps mockNetconfService;
    @Mock
    private AnyxmlNode mockNode;
    private NetconfDataOperations netconfData;
    private DataOperationService operationService;
    private DataStoreService spyService;

    @BeforeEach
    public void beforeEach() {
        final var testId = new RemoteDeviceId("testId", mock(InetSocketAddress.class));

        final var candidatePreferencies = new NetconfSessionPreferences(ImmutableMap.of(CapabilityURN.CANDIDATE,
            mock(CapabilityOrigin.class)), ImmutableMap.of(), null);
        spyService = spy(AbstractDataStore.of(testId, mockNetconfService, candidatePreferencies, true));
        operationService = new DataOperationImpl(spyService);
        netconfData = new NetconfDataOperations(operationService);
    }

    @Override
    AbstractServerDataOperations testDeleteDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    AbstractServerDataOperations testNegativeDeleteDataStrategy() {
        mockLockUnlockDiscard();

        doReturn(mockNode).when(mockNetconfService).createNode(any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        doReturn(Futures.immediateFailedFuture(new NetconfDocumentedException("Data missing",
            ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR))).when(mockNetconfService)
            .commit(any(NetconfRpcFutureCallback.class));
        return netconfData;
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
        doReturn(immediateFluentFuture(Optional.of(songListData))).when(spyService)
            .read(CONFIGURATION, songListWildcardPath, songKeyFields);
        // list elements expected to be deleted one by one
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(spyService)
            .delete(song1Path);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(spyService)
            .delete(song2Path);

        netconfData.deleteData(emptyRequest, jukeboxPath(songListPath));
        verify(spyService, timeout(1000)).read(CONFIGURATION, songListWildcardPath, songKeyFields);
        verify(spyService, timeout(1000)).delete(song1Path);
        verify(spyService, timeout(1000)).delete(song2Path);
        verify(spyService, never()).delete(songListPath);
        emptyRequest.getResult();
    }

    @Override
    AbstractServerDataOperations testPostContainerDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    AbstractServerDataOperations testPostListDataStrategy(final MapEntryNode entryNode,
            final YangInstanceIdentifier node) {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfService).createNode(entryNode, CREATE, node);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    AbstractServerDataOperations testPostDataFailStrategy(final DOMException domException) {
        mockLockUnlockDiscard();
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(immediateFailedFluentFuture(domException)).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    AbstractServerDataOperations testPatchContainerDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    AbstractServerDataOperations testPatchLeafDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    AbstractServerDataOperations testPatchListDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Test
    void testPutCreateContainerData() throws Exception {
        mockLockUnlockCommit();

        doReturn(immediateFluentFuture(Optional.empty())).when(spyService).read(CONFIGURATION,
            JUKEBOX_IID, List.of());

        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        verify(spyService, timeout(1000)).read(CONFIGURATION, JUKEBOX_IID, List.of());
        verify(spyService, timeout(1000)).put(JUKEBOX_IID, EMPTY_JUKEBOX);
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutReplaceContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(spyService)
            .read(CONFIGURATION, JUKEBOX_IID, List.of());

        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        verify(spyService, timeout(1000)).read(CONFIGURATION, JUKEBOX_IID, List.of());
        verify(spyService, timeout(1000)).put(JUKEBOX_IID, EMPTY_JUKEBOX);
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutCreateLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(spyService).read(CONFIGURATION, GAP_IID, List.of());

        doReturn(mockNode).when(mockNetconfService).createNode(GAP_LEAF, REPLACE, GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        verify(spyService, timeout(1000)).read(CONFIGURATION, GAP_IID, List.of());
        verify(spyService, timeout(1000)).put(GAP_IID, GAP_LEAF);
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutReplaceLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(spyService)
            .read(CONFIGURATION, GAP_IID, List.of());

        doReturn(mockNode).when(mockNetconfService).createNode(GAP_LEAF, REPLACE, GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        verify(spyService, timeout(1000)).read(CONFIGURATION, GAP_IID, List.of());
        verify(spyService, timeout(1000)).put(GAP_IID, GAP_LEAF);
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutCreateListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(spyService).read(CONFIGURATION, GAP_IID, List.of());

        doReturn(mockNode).when(mockNetconfService).createNode(JUKEBOX_WITH_BANDS, REPLACE, JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        verify(spyService, timeout(1000)).read(CONFIGURATION, GAP_IID, List.of());
        verify(spyService, timeout(1000)).put(JUKEBOX_IID, JUKEBOX_WITH_BANDS);
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

        doReturn(immediateFluentFuture(Optional.empty())).when(spyService).read(eq(CONFIGURATION), any(), any());

        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        final var songListPath = YangInstanceIdentifier.builder().node(JUKEBOX_QNAME).node(PLAYLIST_QNAME)
            .node(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "0")).node(SONG_QNAME).build();
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(spyService)
            .read(CONFIGURATION, songListPath, List.of());

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
        NetconfDataOperations spyOperations = spy(netconfData);
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
        verify(spyService, timeout(1000).times(3)).put(any(), any());
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

        doReturn(immediateFluentFuture(Optional.empty())).when(spyService).read(eq(CONFIGURATION), any(), any());

        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        final var songListPath = YangInstanceIdentifier.builder().node(JUKEBOX_QNAME).node(PLAYLIST_QNAME)
            .node(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "0")).node(SONG_QNAME).build();
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(spyService)
            .read(CONFIGURATION, songListPath, List.of());

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
        final var spyOperations = netconfData;
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
        verify(spyService, timeout(1000).times(3)).put(any(), any());
        verify(request, timeout(1000)).onSuccess(any());
    }

    @Test
    void testPutReplaceListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(spyService)
            .read(CONFIGURATION, JUKEBOX_IID, List.of());

        doReturn(mockNode).when(mockNetconfService).createNode(JUKEBOX_WITH_BANDS, REPLACE, JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        verify(spyService, timeout(1000)).read(CONFIGURATION, JUKEBOX_IID, List.of());
        verify(spyService, timeout(1000)).put(JUKEBOX_IID, JUKEBOX_WITH_BANDS);
        assertNotNull(dataPutRequest.getResult());
    }

    @Override
    AbstractServerDataOperations testPatchDataReplaceMergeAndRemoveStrategy() {
        mockLockUnlockCommit();
        // FIXME: exact match
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    AbstractServerDataOperations testPatchDataCreateAndDeleteStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfService).createNode(EMPTY_JUKEBOX, CREATE, PLAYER_IID);
        doReturn(mockNode).when(mockNetconfService).createNode(DELETE, GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    AbstractServerDataOperations testPatchMergePutContainerStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfService).createNode(any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    AbstractServerDataOperations deleteNonexistentDataTestStrategy() {
        mockLockUnlockDiscard();

        doReturn(mockNode).when(mockNetconfService).createNode(DELETE, GAP_IID);
        doReturn(Futures.immediateFailedFuture(new NetconfDocumentedException("Data missing", ErrorType.RPC,
                ErrorTag.DATA_MISSING, ErrorSeverity.ERROR)))
            .when(mockNetconfService).commit(any(NetconfRpcFutureCallback.class));
        return netconfData;
    }

    @Override
    void assertTestDeleteNonexistentData(final PatchStatusContext status, final PatchStatusEntity edit) {
        assertNull(edit.getEditErrors());
        final var globalErrors = status.globalErrors();
        assertNotNull(globalErrors);
        assertEquals(1, globalErrors.size());
        final var globalError = globalErrors.get(0);
        assertEquals(new ErrorInfo("Data missing"),
            globalError.info());
        assertEquals(ErrorType.RPC, globalError.type());
        assertEquals(ErrorTag.DATA_MISSING, globalError.tag());
    }

    @Override
    AbstractServerDataOperations readDataConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyService).read(CONFIGURATION, PATH, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readAllHavingOnlyConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyService).read(CONFIGURATION, PATH, List.of());
        doReturn(immediateFluentFuture(Optional.empty())).when(spyService).read(OPERATIONAL, PATH, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(spyService).read(OPERATIONAL, PATH_2, List.of());
        doReturn(immediateFluentFuture(Optional.empty())).when(spyService).read(CONFIGURATION, PATH_2, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readDataNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(spyService).read(OPERATIONAL, PATH_2, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readContainerDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyService).read(CONFIGURATION, PATH, List.of());
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(spyService).read(OPERATIONAL,PATH, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyService).read(CONFIGURATION, PATH, List.of());
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(spyService).read(OPERATIONAL, PATH, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(spyService).read(OPERATIONAL, PATH_3, List.of());
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(spyService).read(CONFIGURATION, PATH_3,
            List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readOrderedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(spyService).read(OPERATIONAL,
            PATH_3, List.of());
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(spyService).read(CONFIGURATION,
            PATH_3, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readUnkeyedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(spyService).read(OPERATIONAL,
            PATH_3, List.of());
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(spyService).read(CONFIGURATION,
            PATH_3, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(spyService)
            .read(OPERATIONAL, LEAF_SET_NODE_PATH, List.of());
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(spyService)
            .read(CONFIGURATION, LEAF_SET_NODE_PATH, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readOrderedLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(spyService)
            .read(OPERATIONAL, LEAF_SET_NODE_PATH, List.of());
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(spyService)
            .read(CONFIGURATION, LEAF_SET_NODE_PATH, List.of());
        return netconfData;
    }

    @Override
    AbstractServerDataOperations readDataWrongPathOrNoContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.empty())).when(spyService).read(CONFIGURATION, PATH_2, List.of());
        return netconfData;
    }

    @Override
    NormalizedNode readData(final ContentParam content, final DatabindPath.Data path,
            final AbstractServerDataOperations strategy) {
        try {
            return (operationService)
                .getData(path, new DataGetParams(content, DepthParam.max(), null, null))
                .get(2, TimeUnit.SECONDS)
                .orElse(null);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    private void mockLockUnlock()  {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).unlockCandidate(any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).lockCandidate(any());
    }

    private void mockLockUnlockCommit() {
        mockLockUnlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).commit(any());
    }

    private void mockLockUnlockDiscard() {
        mockLockUnlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfService).discardChanges(any());
    }
}
