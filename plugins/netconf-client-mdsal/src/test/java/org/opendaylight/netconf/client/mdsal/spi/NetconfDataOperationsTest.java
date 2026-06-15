/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
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
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.PatchEntity;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.AbstractServerDataOperationsTest;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.mdsal.MdsalServerStrategy;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.MappingServerRequest;
import org.opendaylight.restconf.server.spi.NotSupportedServerActionOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerMountPointResolver;
import org.opendaylight.restconf.server.spi.NotSupportedServerRpcOperations;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.oper.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yangtools.databind.DatabindContext;
import org.opendaylight.yangtools.databind.DatabindPath.Data;
import org.opendaylight.yangtools.databind.ErrorInfo;
import org.opendaylight.yangtools.databind.ErrorPath;
import org.opendaylight.yangtools.databind.RequestException;
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
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.w3c.dom.DOMException;

@ExtendWith(MockitoExtension.class)
class NetconfDataOperationsTest extends AbstractServerDataOperationsTest {
    // Read mock data
    private static final Data PATH_DATA = modulesPath(PATH, MODULES_DATABIND);
    private static final Data PATH_2_DATA = modulesPath(PATH_2, MODULES_DATABIND);
    private static final Data PATH_3_DATA = modulesPath(PATH_3, MODULES_DATABIND);
    private static final Data LEAF_SET_NODE_DATA = modulesPath(LEAF_SET_NODE_PATH, MODULES_DATABIND);

    private final CompletingServerRequest<Empty> dataDeleteRequest = new CompletingServerRequest<>();
    private final CompletingServerRequest<DataPatchResult> dataPatchRequest = new CompletingServerRequest<>();
    private final CompletingServerRequest<DataPostResult> dataPostRequest = new CompletingServerRequest<>();
    private final CompletingServerRequest<DataYangPatchResult> dataYangPatchRequest = new CompletingServerRequest<>();

    final CompletingServerRequest<DataPutResult> dataPutRequest = new CompletingServerRequest<>();

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

    /**
     * Test of successful DELETE operation.
     */
    @Test
    void testDeleteData() throws Exception {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(
            DELETE), YangInstanceIdentifier.of());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.deleteData(dataDeleteRequest, new Data(JUKEBOX_DATABIND));
        assertEquals(Empty.value(), dataDeleteRequest.getResult());
    }

    /**
     * Negative test for DELETE operation when data to delete does not exist. Error DATA_MISSING is expected.
     */
    @Test
    void testNegativeDeleteData() {
        mockLockUnlockDiscard();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(),
            Optional.of(DELETE), YangInstanceIdentifier.of());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        doReturn(Futures.immediateFailedFuture(new NetconfDocumentedException("Data missing",
            ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR))).when(mockNetconfBaseOps)
            .commit(any(NetconfRpcFutureCallback.class));

        netconfData.deleteData(dataDeleteRequest, new Data(JUKEBOX_DATABIND));
        final var errors = assertThrows(RequestException.class, dataDeleteRequest::getResult).errors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.DATA_MISSING, error.tag());
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

    @Test
    void testPostContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(CREATE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.createData(dataPostRequest, JUKEBOX_PATH, jukeboxPayload(EMPTY_JUKEBOX));
        assertNotNull(dataPostRequest.getResult());
    }

    @Test
    void testPostListData() throws Exception {
        mockLockUnlockCommit();
        final var node = PLAYLIST_IID.node(BAND_ENTRY.name());
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(BAND_ENTRY),
            Optional.of(CREATE), node);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.createData(dataPostRequest, jukeboxPath(PLAYLIST_IID), jukeboxPayload(PLAYLIST));
        assertNotNull(dataPostRequest.getResult());
    }

    @Test
    void testPostDataFail() {
        final var domException = new DOMException((short) 414, "Post request failed");
        mockLockUnlockDiscard();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(CREATE), JUKEBOX_IID);
        doReturn(immediateFailedFluentFuture(domException)).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.createData(dataPostRequest, JUKEBOX_PATH, jukeboxPayload(EMPTY_JUKEBOX));

        final var errors = assertThrows(RequestException.class, dataPostRequest::getResult).errors();
        assertEquals(1, errors.size());
        final var info = assertInstanceOf(ErrorInfo.OfLiteral.class, errors.getFirst().info());
        assertThat(info.elementBody()).contains(domException.getMessage());
    }

    @Test
    void testPatchContainerData() throws Exception {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(MERGE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.mergeData(dataPatchRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        dataPatchRequest.getResult();
    }

    @Test
    void testPatchLeafData() throws Exception {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(GAP_LEAF),
            Optional.of(MERGE), GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.mergeData(dataPatchRequest, GAP_PATH, GAP_LEAF);
        dataPatchRequest.getResult();
    }

    @Test
    void testPatchListData() throws Exception {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(JUKEBOX_WITH_PLAYLIST),
            Optional.of(MERGE), JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        netconfData.mergeData(dataPatchRequest, JUKEBOX_PATH, JUKEBOX_WITH_PLAYLIST);
        dataPatchRequest.getResult();
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
        assertEquals(
            new ErrorInfo.OfLiteral("RPC during tx failed. Requested resource already lockedUser callback failed. "),
            serverError.info());
    }

    @Test
    void testPatchDataReplaceMergeAndRemove() {
        final var buildArtistList = ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(ARTIST_QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(ARTIST_QNAME, NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "description of artist"))
                .build())
            .build();

        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(buildArtistList),
            Optional.of(MERGE), ARTIST_IID);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(),
            Optional.of(REMOVE), ARTIST_CHILD_IID);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(buildArtistList.body()
            .iterator().next()), Optional.of(REPLACE), ARTIST_CHILD_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        patch(new PatchContext("patchRMRm",
                List.of(new PatchEntity("edit1", Operation.Replace, ARTIST_DATA, buildArtistList),
                    new PatchEntity("edit2", Operation.Merge, ARTIST_DATA, buildArtistList),
                    new PatchEntity("edit3", Operation.Remove, ARTIST_CHILD_DATA))),
            false, ARTIST_DATA.databind());
    }

    @Test
    void testPatchDataCreateAndDelete() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(CREATE), PLAYER_IID);
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(DELETE),
            GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        patch(new PatchContext("patchCD", List.of(
                new PatchEntity("edit1", Operation.Create, PLAYER_DATA, EMPTY_JUKEBOX),
                new PatchEntity("edit2", Operation.Delete, GAP_PATH))),
            true, PLAYER_DATA.databind());
    }

    @MethodSource
    private static List<PatchContext> patchContext() {
        final var buildArtistList = ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(ARTIST_QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(ARTIST_QNAME, NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "description of artist"))
                .build())
            .build();
        return List.of(
            new PatchContext("VerifyNotExecutingLastPatchEntity", List.of(
                new PatchEntity("edit1", Operation.Replace, ARTIST_DATA, buildArtistList),
                new PatchEntity("edit2", Operation.Create, PLAYER_DATA, EMPTY_JUKEBOX),
                new PatchEntity("edit3", Operation.Create, PLAYER_DATA, EMPTY_JUKEBOX))),
            new PatchContext("VerifyExceptionOnLastPatchEntity", List.of(
                new PatchEntity("edit1", Operation.Replace, ARTIST_DATA, buildArtistList),
                new PatchEntity("edit2", Operation.Create, PLAYER_DATA, EMPTY_JUKEBOX)))
        );
    }

    @ParameterizedTest
    @MethodSource("patchContext")
    void testPatchWithDataExistException(final PatchContext patchContext) throws Exception {
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

        // Prepare patch request.
        netconfData.patchData(dataYangPatchRequest, new Data(ARTIST_DATA.databind()), patchContext);

        // Get patch result.
        final var patchStatusContext = dataYangPatchRequest.getResult().status();

        // Verify failure and confirm that edit3 operation was not executed.
        assertFalse(patchStatusContext.ok());
        assertNull(patchStatusContext.globalErrors());
        assertEquals(2, patchStatusContext.editCollection().size());

        // Verify that first request is without errors.
        final var delete = patchStatusContext.editCollection().getFirst();
        assertTrue(delete.isOk());
        assertEquals("edit1", delete.getEditId());
        assertNull(delete.getEditErrors());

        // Verify that second request failed on DATA_EXISTS.
        final var firstCreate = patchStatusContext.editCollection().getLast();
        assertFalse(firstCreate.isOk());
        assertEquals("edit2", firstCreate.getEditId());
        assertNotNull(firstCreate.getEditErrors());
        final var serverError = firstCreate.getEditErrors().getFirst();
        assertEquals(ErrorTag.DATA_EXISTS, serverError.tag());
        assertEquals(new ErrorPath(PLAYER_DATA), serverError.path());
        assertNotNull(serverError.message());
        assertEquals("Data already exists", serverError.message().elementBody());
    }

    @Test
    void testPatchMergePutContainer() {
        mockLockUnlockCommit();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.of(EMPTY_JUKEBOX),
            Optional.of(MERGE), PLAYER_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));

        patch(new PatchContext("patchM", List.of(new PatchEntity("edit1", Operation.Merge, PLAYER_DATA,
            EMPTY_JUKEBOX))), false, PLAYER_DATA.databind());
    }

    @Test
    void testDeleteNonexistentData() throws Exception {
        mockLockUnlockDiscard();
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(Optional.empty(), Optional.of(DELETE),
            GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(mockNetconfBaseOps)
            .editConfigCandidate(any(NetconfRpcFutureCallback.class), eq(mockNode), eq(false));
        doReturn(Futures.immediateFailedFuture(new NetconfDocumentedException("Data missing", ErrorType.RPC,
            ErrorTag.DATA_MISSING, ErrorSeverity.ERROR)))
            .when(mockNetconfBaseOps).commit(any(NetconfRpcFutureCallback.class));

        netconfData.patchData(dataYangPatchRequest, new Data(JUKEBOX_DATABIND),
            new PatchContext("patchD", List.of(new PatchEntity("edit1", Operation.Delete, GAP_PATH))));

        final var status = dataYangPatchRequest.getResult().status();
        assertEquals("patchD", status.patchId());
        assertFalse(status.ok());
        final var edits = status.editCollection();
        assertEquals(1, edits.size());
        final var edit = edits.get(0);
        assertEquals("edit1", edit.getEditId());

        assertNull(edit.getEditErrors());
        final var globalErrors = status.globalErrors();
        assertNotNull(globalErrors);
        assertEquals(1, globalErrors.size());
        final var globalError = globalErrors.getFirst();
        assertEquals(new ErrorInfo.OfLiteral("Data missing"), globalError.info());
        assertEquals(ErrorType.RPC, globalError.type());
        assertEquals(ErrorTag.DATA_MISSING, globalError.tag());
    }

    @Test
    void readDataConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyDataStoreService).get(CONFIGURATION, PATH,
            List.of());
        assertEquals(DATA_3, readData(ContentParam.CONFIG, PATH_DATA));
    }

    @Test
    void readAllHavingOnlyConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyDataStoreService).get(CONFIGURATION, PATH,
            List.of());
        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(OPERATIONAL, PATH,
            List.of());
        assertEquals(DATA_3, readData(ContentParam.ALL, PATH_DATA));
    }

    @Test
    void readAllHavingOnlyNonConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(spyDataStoreService).get(OPERATIONAL, PATH_2,
            List.of());
        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(CONFIGURATION, PATH_2,
            List.of());
        assertEquals(DATA_2, readData(ContentParam.ALL, PATH_2_DATA));
    }

    @Test
    void readDataNonConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(spyDataStoreService).get(OPERATIONAL, PATH_2,
            List.of());
        assertEquals(DATA_2, readData(ContentParam.NONCONFIG, PATH_2_DATA));
    }

    @Test
    void readContainerDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyDataStoreService).get(CONFIGURATION, PATH,
            List.of());
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(spyDataStoreService).get(OPERATIONAL,PATH,
            List.of());

        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH_DATA));
    }

    @Test
    void readContainerDataConfigNoValueOfContentTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(spyDataStoreService).get(CONFIGURATION, PATH,
            List.of());
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(spyDataStoreService).get(OPERATIONAL, PATH,
            List.of());

        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH_DATA));
    }

    @Test
    void readListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(spyDataStoreService).get(OPERATIONAL, PATH_3,
            List.of());
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(spyDataStoreService).get(CONFIGURATION, PATH_3,
            List.of());

        assertEquals(ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3_DATA));
    }

    @Test
    void readOrderedListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(spyDataStoreService).get(OPERATIONAL,
            PATH_3, List.of());
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(spyDataStoreService).get(CONFIGURATION,
            PATH_3, List.of());

        assertEquals(ImmutableNodes.newUserMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3_DATA));
    }

    @Test
    void readUnkeyedListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(spyDataStoreService).get(OPERATIONAL,
            PATH_3, List.of());
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(spyDataStoreService).get(CONFIGURATION,
            PATH_3, List.of());

        assertEquals(ImmutableNodes.newUnkeyedListBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(ImmutableNodes.newUnkeyedListEntryBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(UNKEYED_LIST_ENTRY_NODE_1.body().iterator().next())
                .withChild(UNKEYED_LIST_ENTRY_NODE_2.body().iterator().next())
                .build())
            .build(), readData(ContentParam.ALL, PATH_3_DATA));
    }

    @Test
    void readLeafListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(spyDataStoreService)
            .get(OPERATIONAL, LEAF_SET_NODE_PATH, List.of());
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(spyDataStoreService)
            .get(CONFIGURATION, LEAF_SET_NODE_PATH, List.of());

        assertEquals(ImmutableNodes.<String>newSystemLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(LEAF_SET_NODE_1.body())
                .addAll(LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_DATA));
    }

    @Test
    void readOrderedLeafListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(spyDataStoreService)
            .get(OPERATIONAL, LEAF_SET_NODE_PATH, List.of());
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(spyDataStoreService)
            .get(CONFIGURATION, LEAF_SET_NODE_PATH, List.of());

        assertEquals(ImmutableNodes.<String>newUserLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(ORDERED_LEAF_SET_NODE_1.body())
                .addAll(ORDERED_LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_DATA));
    }

    @Test
    void readDataWrongPathOrNoContentTest() {
        doReturn(immediateFluentFuture(Optional.empty())).when(spyDataStoreService).get(CONFIGURATION, PATH_2,
            List.of());
        assertNull(readData(ContentParam.CONFIG, PATH_2_DATA));
    }

    /**
     * Read specific type of data from data store via transaction.
     *
     * @param content        type of data to read (config, state, all)
     * @param path           path to data
     * @return {@link NormalizedNode}
     */
    private @Nullable NormalizedNode readData(ContentParam content, Data path) {
        try {
            return dataOperationService
                .getData(path, new DataGetParams(content, DepthParam.max(), null, null))
                .get(2, TimeUnit.SECONDS)
                .orElse(null);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    private void patch(final PatchContext patchContext, final boolean failed, final DatabindContext context) {
        netconfData.patchData(dataYangPatchRequest, new Data(context), patchContext);

        final PatchStatusContext patchStatusContext;
        try {
            patchStatusContext = dataYangPatchRequest.getResult().status();
        } catch (RequestException | InterruptedException | TimeoutException e) {
            throw new AssertionError(e);
        }

        for (var entity : patchStatusContext.editCollection()) {
            if (failed) {
                assertTrue(entity.isOk(), "Edit " + entity.getEditId() + " failed");
            } else {
                assertTrue(entity.isOk());
            }
        }
        assertTrue(patchStatusContext.ok());
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
