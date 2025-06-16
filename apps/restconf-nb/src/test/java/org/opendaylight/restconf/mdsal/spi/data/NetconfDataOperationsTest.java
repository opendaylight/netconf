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
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.netconf.api.EffectiveOperation.CREATE;
import static org.opendaylight.netconf.api.EffectiveOperation.DELETE;
import static org.opendaylight.netconf.api.EffectiveOperation.MERGE;
import static org.opendaylight.netconf.api.EffectiveOperation.REMOVE;
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
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNullByDefault;
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
import org.opendaylight.netconf.client.mdsal.spi.DataOperationsService;
import org.opendaylight.netconf.client.mdsal.spi.DataOperationsServiceImpl;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDataOperations;
import org.opendaylight.netconf.databind.DatabindPath.Data;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.w3c.dom.DOMException;

@ExtendWith(MockitoExtension.class)
final class NetconfDataOperationsTest extends AbstractServerDataOperationsTest {
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
    private NetconfBaseOps mockNetconfBaseOps;
    @Mock
    private ChoiceNode mockNode;

    private NetconfDataOperations netconfData;
    private DataOperationsService dataOperationService;
    private DataStoreService spyDataStoreService;

    @BeforeEach
    public void beforeEach() {
        final var testId = new RemoteDeviceId("testId", mock(InetSocketAddress.class));

        final var candidatePreferencies = new NetconfSessionPreferences(ImmutableMap.of(CapabilityURN.CANDIDATE,
            mock(CapabilityOrigin.class)), ImmutableMap.of(), null);
        spyDataStoreService = spy(AbstractDataStore.of(testId, mockNetconfBaseOps, candidatePreferencies, true));
        dataOperationService = new DataOperationsServiceImpl(spyDataStoreService);
        netconfData = new NetconfDataOperations(dataOperationService);
    }

    @Override
    ServerDataOperations testDeleteDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(
            DELETE), YangInstanceIdentifier.of());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations testNegativeDeleteDataStrategy() {
        mockLockUnlockDiscard();

        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(),
            Optional.of(DELETE), YangInstanceIdentifier.of());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        doReturn(Futures.immediateFailedFuture(new NetconfDocumentedException("Data missing",
            ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR))).when(mockNetconfBaseOps)
            .commit(any(NetconfRpcFutureCallback.class));
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
        doReturn(immediateFluentFuture(Optional.of(songListData))).when(spyDataStoreService)
            .get(CONFIGURATION, songListWildcardPath, songKeyFields);
        // list elements expected to be deleted one by one

        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(DELETE),
            SONG1_PATH);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(DELETE),
            SONG2_PATH);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.deleteData(emptyRequest, jukeboxPath(SONG_LIST_PATH));
        emptyRequest.getResult();
        verify(spyDataStoreService).get(CONFIGURATION, songListWildcardPath, songKeyFields);
        verify(spyDataStoreService).delete(SONG1_PATH);
        verify(spyDataStoreService).delete(SONG2_PATH);
        verify(spyDataStoreService, never()).delete(SONG_LIST_PATH);
    }

    @Override
    ServerDataOperations testPostContainerDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(CREATE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations testPostListDataStrategy(final MapEntryNode entryNode,
            final YangInstanceIdentifier node) {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(entryNode),
            Optional.of(CREATE), node);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations testPostDataFailStrategy(final DOMException domException) {
        mockLockUnlockDiscard();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(CREATE), JUKEBOX_IID);
        doReturn(immediateFailedFluentFuture(domException)).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchContainerDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(MERGE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchLeafDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(GAP_LEAF),
            Optional.of(MERGE), GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchListDataStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(JUKEBOX_WITH_PLAYLIST),
            Optional.of(MERGE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Test
    void testPutCreateContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(CONFIGURATION,
            JUKEBOX_IID, List.of());
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(REPLACE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        assertNotNull(dataPutRequest.getResult());
        verify(spyDataStoreService).get(CONFIGURATION, JUKEBOX_IID, List.of());
        verify(spyDataStoreService).replace(JUKEBOX_IID, EMPTY_JUKEBOX);
    }

    @Test
    void testPutReplaceContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(spyDataStoreService)
            .get(CONFIGURATION, JUKEBOX_IID, List.of());
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(REPLACE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        assertNotNull(dataPutRequest.getResult());
        verify(spyDataStoreService, timeout(1000)).get(CONFIGURATION, JUKEBOX_IID, List.of());
        verify(spyDataStoreService, timeout(1000)).replace(JUKEBOX_IID, EMPTY_JUKEBOX);
    }

    @Test
    void testPutCreateLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(CONFIGURATION, GAP_IID,
            List.of());
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(GAP_LEAF),
            Optional.of(REPLACE), GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        assertNotNull(dataPutRequest.getResult());
        verify(spyDataStoreService).get(CONFIGURATION, GAP_IID, List.of());
        verify(spyDataStoreService).replace(GAP_IID, GAP_LEAF);
    }

    @Test
    void testPutReplaceLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(spyDataStoreService)
            .get(CONFIGURATION, GAP_IID, List.of());
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(GAP_LEAF),
            Optional.of(REPLACE), GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        assertNotNull(dataPutRequest.getResult());
        verify(spyDataStoreService).get(CONFIGURATION, GAP_IID, List.of());
        verify(spyDataStoreService).replace(GAP_IID, GAP_LEAF);
    }

    @Test
    void testPutCreateListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(CONFIGURATION,
            JUKEBOX_IID, List.of());
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(JUKEBOX_WITH_BANDS),
            Optional.of(REPLACE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        assertNotNull(dataPutRequest.getResult());
        verify(spyDataStoreService).get(CONFIGURATION, JUKEBOX_IID, List.of());
        verify(spyDataStoreService).replace(JUKEBOX_IID, JUKEBOX_WITH_BANDS);
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

        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(CONFIGURATION, SONG3_PATH,
            List.of());
        final var song3Node = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME, Uint32.valueOf(3)))
            .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "C"))
            .build();

        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(SONG1), Optional.of(REPLACE),
            SONG1_PATH);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(SONG2), Optional.of(REPLACE),
            SONG2_PATH);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(song3Node),
            Optional.of(REPLACE), SONG3_PATH);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(REMOVE),
            SONG1_PATH);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(REMOVE),
            SONG2_PATH);

        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(spyDataStoreService)
            .get(CONFIGURATION, SONG_LIST_PATH, List.of());

        // Inserting new song at 3rd position (aka as last element)
        final var request = spy(new TestServerRequest<DataPutResult>(QueryParameters.of(
            // insert new item after last existing item in list
            InsertParam.AFTER, PointParam.forUriValue("example-jukebox:jukebox/playlist=0/song=2")),
            PrettyPrintParam.TRUE));

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

        verify(spyDataStoreService).remove(SONG1_PATH);
        verify(spyDataStoreService).remove(SONG2_PATH);
        verify(spyDataStoreService).get(CONFIGURATION, SONG_LIST_PATH, List.of());
        verify(spyDataStoreService).commit();

        // Counting how many times we insert items in list
        verify(spyDataStoreService).replace(SONG1_PATH, SONG1);
        verify(spyDataStoreService).replace(SONG2_PATH, SONG2);
        verify(spyDataStoreService).replace(SONG3_PATH, song3Node);
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

        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(CONFIGURATION, SONG3_PATH,
            List.of());
        final var song3Node = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME, Uint32.valueOf(3)))
            .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "C"))
            .build();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(SONG1), Optional.of(REPLACE),
            SONG1_PATH);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(SONG2), Optional.of(REPLACE),
            SONG2_PATH);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(song3Node),
            Optional.of(REPLACE), SONG3_PATH);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(REMOVE),
            SONG1_PATH);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(REMOVE),
            SONG2_PATH);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(spyDataStoreService)
            .get(CONFIGURATION, SONG_LIST_PATH, List.of());

        final var request = spy(new TestServerRequest<DataPostResult>(QueryParameters.of(InsertParam.AFTER,
            PointParam.forUriValue("example-jukebox:jukebox/playlist=0/song=2")), PrettyPrintParam.FALSE));

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

        verify(spyDataStoreService).remove(SONG1_PATH);
        verify(spyDataStoreService).remove(SONG2_PATH);
        verify(spyDataStoreService).get(CONFIGURATION, SONG_LIST_PATH, List.of());
        verify(spyDataStoreService).get(CONFIGURATION, SONG3_PATH, List.of());
        verify(spyDataStoreService).commit();

        // Counting how many times we insert items in list
        verify(spyDataStoreService).replace(SONG1_PATH, SONG1);
        verify(spyDataStoreService).replace(SONG2_PATH, SONG2);
        verify(spyDataStoreService).replace(SONG3_PATH, song3Node);
    }

    @Test
    void testPutReplaceListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(immediateFluentFuture(Optional.of(mock(ContainerNode.class)))).when(spyDataStoreService)
            .get(CONFIGURATION, JUKEBOX_IID, List.of());

        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(JUKEBOX_WITH_BANDS),
            Optional.of(REPLACE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        assertNotNull(dataPutRequest.getResult());
        verify(spyDataStoreService).get(CONFIGURATION, JUKEBOX_IID, List.of());
        verify(spyDataStoreService).replace(JUKEBOX_IID, JUKEBOX_WITH_BANDS);
    }

    @Test
    void testLockOperationException() throws Exception {
        // Prepare environment.
        final var rpcError = RpcResultBuilder.newError(ErrorType.PROTOCOL, ErrorTag.OPERATION_FAILED,
            "Requested resource already lockedUser callback failed.", null, "", null);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcError))).when(mockNetconfBaseOps).lockCandidate(
            any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps).unlockCandidate(any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps).discardChanges(any());

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
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(artistList),
            Optional.of(MERGE), ARTIST_IID);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(),
            Optional.of(REMOVE), ARTIST_CHILD_IID);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(artistList.body().iterator()
                .next()), Optional.of(REPLACE), ARTIST_CHILD_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchDataCreateAndDeleteStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(CREATE), PLAYER_IID);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(DELETE),
            GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchWithDataExistExceptionStrategy() {
        mockLockUnlockDiscard();
        final var rpcError = RpcResultBuilder.newError(ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
            "Data already exists", null, "", null);

        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(any(Optional.class),
            eq(Optional.of(REPLACE)), eq(ARTIST_CHILD_IID));
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        final var failChoice = mock(ChoiceNode.class);
        doReturn(failChoice).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(CREATE), PLAYER_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcError))).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(failChoice), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations testPatchMergePutContainerStrategy() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(MERGE), PLAYER_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        return netconfData;
    }

    @Override
    ServerDataOperations deleteNonexistentDataTestStrategy() {
        mockLockUnlockDiscard();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(DELETE),
            GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        doReturn(Futures.immediateFailedFuture(new NetconfDocumentedException("Data missing", ErrorType.RPC,
                ErrorTag.DATA_MISSING, ErrorSeverity.ERROR)))
            .when(mockNetconfBaseOps).commit(any(NetconfRpcFutureCallback.class));
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
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyDataStoreService).get(CONFIGURATION, PATH,
            List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readAllHavingOnlyConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyDataStoreService).get(CONFIGURATION, PATH,
            List.of());
        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(OPERATIONAL, PATH,
            List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(spyDataStoreService).get(OPERATIONAL, PATH_2,
            List.of());
        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(CONFIGURATION, PATH_2,
            List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readDataNonConfigTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(spyDataStoreService).get(OPERATIONAL, PATH_2,
            List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readContainerDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyDataStoreService).get(CONFIGURATION, PATH,
            List.of());
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(spyDataStoreService).get(OPERATIONAL,PATH,
            List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyDataStoreService).get(CONFIGURATION, PATH,
            List.of());
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(spyDataStoreService).get(OPERATIONAL, PATH,
            List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(spyDataStoreService).get(OPERATIONAL, PATH_3,
            List.of());
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(spyDataStoreService).get(CONFIGURATION, PATH_3,
            List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readOrderedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(spyDataStoreService).get(OPERATIONAL,
            PATH_3, List.of());
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(spyDataStoreService).get(CONFIGURATION,
            PATH_3, List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readUnkeyedListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(spyDataStoreService).get(OPERATIONAL,
            PATH_3, List.of());
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(spyDataStoreService).get(CONFIGURATION,
            PATH_3, List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(spyDataStoreService)
            .get(OPERATIONAL, LEAF_SET_NODE_PATH, List.of());
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(spyDataStoreService)
            .get(CONFIGURATION, LEAF_SET_NODE_PATH, List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readOrderedLeafListDataAllTestStrategy() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(spyDataStoreService)
            .get(OPERATIONAL, LEAF_SET_NODE_PATH, List.of());
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(spyDataStoreService)
            .get(CONFIGURATION, LEAF_SET_NODE_PATH, List.of());
        return netconfData;
    }

    @Override
    ServerDataOperations readDataWrongPathOrNoContentTestStrategy() {
        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(CONFIGURATION, PATH_2,
            List.of());
        return netconfData;
    }

    @Override
    void assertReadDataWrongPathOrNoContent(final Supplier<NormalizedNode> readResult) {
        assertNull(readResult.get());
    }

    @Override
    NormalizedNode readData(final ContentParam content, final Data path, final ServerDataOperations strategy) {
        try {
            return (dataOperationService)
                .getData(path, new DataGetParams(content, DepthParam.max(), null, null))
                .get(2, TimeUnit.SECONDS)
                .orElse(null);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    private void mockLockUnlock()  {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps).unlockCandidate(any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps).lockCandidate(any());
    }

    private void mockLockUnlockCommit() {
        mockLockUnlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps).commit(any());
    }

    private void mockLockUnlockDiscard() {
        mockLockUnlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps).discardChanges(any());
    }
}
