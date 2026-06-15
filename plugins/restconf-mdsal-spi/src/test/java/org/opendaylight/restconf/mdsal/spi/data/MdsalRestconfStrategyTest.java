/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.PatchEntity;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.testlib.AbstractServerDataOperationsTest;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.databind.DatabindContext;
import org.opendaylight.yangtools.databind.DatabindPath.Data;
import org.opendaylight.yangtools.databind.ErrorInfo;
import org.opendaylight.yangtools.databind.ErrorPath;
import org.opendaylight.yangtools.databind.RequestException;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorMessage;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.DOMException;

@ExtendWith(MockitoExtension.class)
class MdsalRestconfStrategyTest extends AbstractServerDataOperationsTest {
    private static final DatabindContext MODULES_DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResourceDirectory("/modules"));
    private static final Data PATH_DATA = moudlesPath(PATH, MODULES_DATABIND);
    private static final Data PATH_2_DATA = moudlesPath(PATH_2, MODULES_DATABIND);
    private static final Data PATH_3_DATA = moudlesPath(PATH_3, MODULES_DATABIND);
    private static final Data LEAF_SET_NODE_DATA = moudlesPath(LEAF_SET_NODE_PATH, MODULES_DATABIND);

    private final CompletingServerRequest<Empty> dataDeleteRequest = new CompletingServerRequest<>();
    private final CompletingServerRequest<DataPatchResult> dataPatchRequest = new CompletingServerRequest<>();
    private final CompletingServerRequest<DataPostResult> dataPostRequest = new CompletingServerRequest<>();
    private final CompletingServerRequest<DataYangPatchResult> dataYangPatchRequest = new CompletingServerRequest<>();

    final CompletingServerRequest<DataPutResult> dataPutRequest = new CompletingServerRequest<>();
    final CompletingServerRequest<Optional<NormalizedNode>> getServerRequest = new CompletingServerRequest<>();

    private static final YangInstanceIdentifier CONT_IID = YangInstanceIdentifier.of(CONT_QNAME);
    private static final Data CONT_DATA = moudlesPath(CONT_IID, MODULES_DATABIND);

    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private EffectiveModelContext mockSchemaContext;

    private @NonNull MdsalRestconfStrategy modulesStrategy() {
        return new MdsalRestconfStrategy(MODULES_DATABIND, dataBroker);
    }

    private MdsalRestconfStrategy jukeboxDataOperations() {
        return new MdsalRestconfStrategy(JUKEBOX_DATABIND, dataBroker);
    }

    private MdsalRestconfStrategy mockDataOperations() {
        return new MdsalRestconfStrategy(DatabindContext.ofModel(mockSchemaContext), dataBroker);
    }

    @Test
    void testDeleteData() throws Exception {
        testDeleteDataStrategy().deleteData(dataDeleteRequest, new Data(JUKEBOX_DATABIND));
        assertEquals(Empty.value(), dataDeleteRequest.getResult());
    }

    private ServerDataOperations testDeleteDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateTrueFluentFuture());
        return jukeboxDataOperations();
    }

    @Test
    void testNegativeDeleteData() {
        testNegativeDeleteDataStrategy().deleteData(dataDeleteRequest, new Data(JUKEBOX_DATABIND));
        final var errors = assertThrows(RequestException.class, dataDeleteRequest::getResult).errors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.DATA_MISSING, error.tag());
    }

    private ServerDataOperations testNegativeDeleteDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateFalseFluentFuture());
        return jukeboxDataOperations();
    }

    @Test
    void testPostContainerData() throws Exception {
        testPostContainerDataStrategy().createData(dataPostRequest, JUKEBOX_PATH, jukeboxPayload(EMPTY_JUKEBOX));
        assertNotNull(dataPostRequest.getResult());
    }

    private ServerDataOperations testPostContainerDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }



    @Test
    void testPostListData() throws Exception {
        testPostListDataStrategy(BAND_ENTRY, PLAYLIST_IID.node(BAND_ENTRY.name()))
            .createData(dataPostRequest, jukeboxPath(PLAYLIST_IID), jukeboxPayload(PLAYLIST));
        assertNotNull(dataPostRequest.getResult());
    }

    private ServerDataOperations testPostListDataStrategy(final MapEntryNode entryNode,
            final YangInstanceIdentifier node) {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, entryNode);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Test
    void testPostDataFail() {
        final var domException = new DOMException((short) 414, "Post request failed");
        testPostDataFailStrategy(domException).createData(dataPostRequest, JUKEBOX_PATH, jukeboxPayload(EMPTY_JUKEBOX));
        final var errors = assertThrows(RequestException.class, dataPostRequest::getResult).errors();
        assertEquals(1, errors.size());
        final var info = assertInstanceOf(ErrorInfo.OfLiteral.class, errors.getFirst().info());
        assertThat(info.elementBody()).contains(domException.getMessage());
    }

    private ServerDataOperations testPostDataFailStrategy(final DOMException domException) {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFailedFluentFuture(domException)).when(readWrite).commit();
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        return jukeboxDataOperations();
    }

    @Test
    void testPutContainerData() throws Exception {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutLeafData() throws Exception {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        jukeboxDataOperations().putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testEnsureParentsByMerge() throws Exception {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        final var strategy = spy(jukeboxDataOperations());
        final var tx = spy(jukeboxDataOperations().prepareWriteExecution());
        doReturn(tx).when(strategy).prepareWriteExecution();

        strategy.putData(dataPutRequest, GAP_PATH, GAP_LEAF);
        verify(tx).merge(eq(GAP_IID.getAncestor(1)), any());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void noEnsureParentsByMergeForTopLevelElements() throws Exception {
        final var topLevelContainer = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME)).build();

        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, topLevelContainer);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        final var strategy = spy(jukeboxDataOperations());
        final var tx = spy(jukeboxDataOperations().prepareWriteExecution());
        doReturn(tx).when(strategy).prepareWriteExecution();

        strategy.putData(dataPutRequest, JUKEBOX_PATH, topLevelContainer);
        verify(tx, never()).merge(any(), any());
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPutListData() throws Exception {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
            .when(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        jukeboxDataOperations().putData(dataPutRequest, JUKEBOX_PATH, JUKEBOX_WITH_BANDS);
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS);
        assertNotNull(dataPutRequest.getResult());
    }

    @Test
    void testPatchContainerData() throws Exception {
        testPatchContainerDataStrategy().mergeData(dataPatchRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        dataPatchRequest.getResult();
    }

    private ServerDataOperations testPatchContainerDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Test
    void testPatchLeafData() throws Exception {
        testPatchLeafDataStrategy().mergeData(dataPatchRequest, GAP_PATH, GAP_LEAF);
        dataPatchRequest.getResult();
    }

    private ServerDataOperations testPatchLeafDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Test
    void testPatchListData() throws Exception {
        testPatchListDataStrategy().mergeData(dataPatchRequest, JUKEBOX_PATH, JUKEBOX_WITH_PLAYLIST);
        dataPatchRequest.getResult();
    }

    private ServerDataOperations testPatchListDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
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

        patch(new PatchContext("patchRMRm",
                List.of(new PatchEntity("edit1", Operation.Replace, ARTIST_DATA, buildArtistList),
                    new PatchEntity("edit2", Operation.Merge, ARTIST_DATA, buildArtistList),
                    new PatchEntity("edit3", Operation.Remove, ARTIST_CHILD_DATA))),
            testPatchDataReplaceMergeAndRemoveStrategy(buildArtistList), false, ARTIST_DATA.databind());
    }

    private ServerDataOperations testPatchDataReplaceMergeAndRemoveStrategy(final MapNode artistList) {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Test
    void testPatchDataCreateAndDelete() {
        patch(new PatchContext("patchCD", List.of(
                new PatchEntity("edit1", Operation.Create, PLAYER_DATA, EMPTY_JUKEBOX),
                new PatchEntity("edit2", Operation.Delete, GAP_PATH))),
            testPatchDataCreateAndDeleteStrategy(), true, PLAYER_DATA.databind());
    }

    private ServerDataOperations testPatchDataCreateAndDeleteStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, PLAYER_IID);
        doReturn(immediateTrueFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        return jukeboxDataOperations();
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
        final var strategy = testPatchWithDataExistExceptionStrategy();
        strategy.patchData(dataYangPatchRequest, new Data(ARTIST_DATA.databind()), patchContext);

        final var patchStatusContext = dataYangPatchRequest.getResult().status();

        assertFalse(patchStatusContext.ok());
        assertNull(patchStatusContext.globalErrors());
        assertEquals(2, patchStatusContext.editCollection().size());

        final var delete = patchStatusContext.editCollection().getFirst();
        assertTrue(delete.isOk());
        assertEquals("edit1", delete.getEditId());
        assertNull(delete.getEditErrors());

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

    private ServerDataOperations testPatchWithDataExistExceptionStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(immediateTrueFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, PLAYER_IID);
        return jukeboxDataOperations();
    }

    @Test
    void testPatchMergePutContainer() {
        patch(new PatchContext("patchM", List.of(new PatchEntity("edit1", Operation.Merge, PLAYER_DATA,
            EMPTY_JUKEBOX))), testPatchMergePutContainerStrategy(), false, PLAYER_DATA.databind());
    }

    private ServerDataOperations testPatchMergePutContainerStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Test
    void testDeleteNonexistentData() throws Exception {
        deleteNonexistentDataTestStrategy().patchData(dataYangPatchRequest, new Data(JUKEBOX_DATABIND),
            new PatchContext("patchD", List.of(new PatchEntity("edit1", Operation.Delete, GAP_PATH))));

        final var status = dataYangPatchRequest.getResult().status();
        assertEquals("patchD", status.patchId());
        assertFalse(status.ok());
        final var edits = status.editCollection();
        assertEquals(1, edits.size());
        final var edit = edits.get(0);
        assertEquals("edit1", edit.getEditId());
        assertTestDeleteNonexistentData(status, edit);
    }

    private ServerDataOperations deleteNonexistentDataTestStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        return jukeboxDataOperations();
    }

    private void assertTestDeleteNonexistentData(final PatchStatusContext status, final PatchStatusEntity edit) {
        assertNull(status.globalErrors());
        final var editErrors = edit.getEditErrors();
        assertEquals(1, editErrors.size());
        final var editError = editErrors.get(0);
        assertEquals(new ErrorMessage("Data does not exist"), editError.message());
        assertEquals(ErrorType.PROTOCOL, editError.type());
        assertEquals(ErrorTag.DATA_MISSING, editError.tag());
    }

    @Test
    void readDataConfigTest() {
        assertEquals(DATA_3, readData(ContentParam.CONFIG, PATH_DATA, readDataConfigTestStrategy()));
    }

    private ServerDataOperations readDataConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        return mockDataOperations();
    }

    @Test
    void readAllHavingOnlyConfigTest() {
        assertEquals(DATA_3, readData(ContentParam.ALL, PATH_DATA, readAllHavingOnlyConfigTestStrategy()));
    }

    private ServerDataOperations readAllHavingOnlyConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return mockDataOperations();
    }

    @Test
    void readAllHavingOnlyNonConfigTest() {
        assertEquals(DATA_2, readData(ContentParam.ALL, PATH_2_DATA, readAllHavingOnlyNonConfigTestStrategy()));
    }

    private ServerDataOperations readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        return mockDataOperations();
    }

    @Test
    void readDataNonConfigTest() {
        assertEquals(DATA_2, readData(ContentParam.NONCONFIG, PATH_2_DATA, readDataNonConfigTestStrategy()));
    }

    private ServerDataOperations readDataNonConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        return mockDataOperations();
    }

    @Test
    void readContainerDataAllTest() {
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH_DATA, readContainerDataAllTestStrategy()));
    }

    private ServerDataOperations readContainerDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return mockDataOperations();
    }

    @Test
    void readContainerDataConfigNoValueOfContentTest() {
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH_DATA, readContainerDataConfigNoValueOfContentTestStrategy()));
    }

    private ServerDataOperations readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return mockDataOperations();
    }

    @Test
    void readListDataAllTest() {
        assertEquals(ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3_DATA, readListDataAllTestStrategy()));
    }

    private ServerDataOperations readListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return mockDataOperations();
    }

    @Test
    void readOrderedListDataAllTest() {
        assertEquals(ImmutableNodes.newUserMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3_DATA, readOrderedListDataAllTestStrategy()));
    }

    private ServerDataOperations readOrderedListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return mockDataOperations();
    }

    @Test
    void readUnkeyedListDataAllTest() {
        assertEquals(ImmutableNodes.newUnkeyedListBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(ImmutableNodes.newUnkeyedListEntryBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(UNKEYED_LIST_ENTRY_NODE_1.body().iterator().next())
                .withChild(UNKEYED_LIST_ENTRY_NODE_2.body().iterator().next())
                .build())
            .build(), readData(ContentParam.ALL, PATH_3_DATA, readUnkeyedListDataAllTestStrategy()));
    }

    private ServerDataOperations readUnkeyedListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return mockDataOperations();
    }

    @Test
    void readLeafListDataAllTest() {
        assertEquals(ImmutableNodes.<String>newSystemLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(LEAF_SET_NODE_1.body())
                .addAll(LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_DATA, readLeafListDataAllTestStrategy()));
    }

    private ServerDataOperations readLeafListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        return mockDataOperations();
    }

    @Test
    void readOrderedLeafListDataAllTest() {
        assertEquals(ImmutableNodes.<String>newUserLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(ORDERED_LEAF_SET_NODE_1.body())
                .addAll(ORDERED_LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_DATA, readOrderedLeafListDataAllTestStrategy()));
    }

    private ServerDataOperations readOrderedLeafListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        return mockDataOperations();
    }

    @Test
    void readDataWrongPathOrNoContentTest() {
        assertReadDataWrongPathOrNoContent(() -> readData(ContentParam.CONFIG, PATH_2_DATA,
            readDataWrongPathOrNoContentTestStrategy()));
    }

    private void assertReadDataWrongPathOrNoContent(final Supplier<NormalizedNode> readResult) {
        final var assertionError = assertThrows(AssertionError.class, readResult::get);
        final var requestException = assertInstanceOf(RequestException.class, assertionError.getCause());
        final var requestError = requestException.errors().getFirst();
        assertNotNull(requestError.message());
        assertEquals("Request could not be completed because the relevant data model content does not exist",
            requestError.message().elementBody());
    }

    private ServerDataOperations readDataWrongPathOrNoContentTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.empty())).when(read).read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        return mockDataOperations();
    }


    private @Nullable NormalizedNode readData(ContentParam content, Data path, ServerDataOperations dataOperations) {
        if (!(dataOperations instanceof MdsalRestconfStrategy mdsalRestconfStrategy)) {
            fail("Wrong ServerDataOperations type" + dataOperations);
            return null;
        }
        try {
            mdsalRestconfStrategy.readData(getServerRequest, content, path, null);
            return getServerRequest.getResult().orElse(null);
        } catch (TimeoutException | InterruptedException | RequestException e) {
            throw new AssertionError(e);
        }
    }

    private void patch(final PatchContext patchContext, final ServerDataOperations strategy,
        final boolean failed, final DatabindContext context) {
        strategy.patchData(dataYangPatchRequest, new Data(context), patchContext);

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

    @Test
    void readLeafWithDefaultParameters() throws Exception {
        final var data = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(BASE, "exampleLeaf"), "i am leaf"))
            .build();

        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION,  CONT_IID);
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, CONT_IID);

        modulesStrategy().readData(getServerRequest, ContentParam.ALL, CONT_DATA, WithDefaultsParam.TRIM);
        final var getResult = getServerRequest.getResult().orElseThrow();
        assertEquals(data, getResult);
    }

    @Test
    void readContainerWithDefaultParameters() throws Exception {
        final var exampleList = new NodeIdentifier(QName.create(BASE, "exampleList"));
        final var data = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_QNAME))
            .withChild(ImmutableNodes.newUnkeyedListBuilder()
                .withNodeIdentifier(exampleList)
                .withChild(ImmutableNodes.newUnkeyedListEntryBuilder()
                    .withNodeIdentifier(exampleList)
                    .withChild(ImmutableNodes.newContainerBuilder()
                        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "containerBool")))
                        .withChild(ImmutableNodes.leafNode(QName.create(BASE, "leafBool"), true))
                        .build())
                    .addChild(ImmutableNodes.newContainerBuilder()
                        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "containerInt")))
                        .withChild(ImmutableNodes.leafNode(QName.create(BASE, "leafInt"), 12))
                        .build())
                    .build())
                .build())
            .build();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, CONT_IID);
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, CONT_IID);

        modulesStrategy().readData(getServerRequest, ContentParam.ALL, CONT_DATA, WithDefaultsParam.TRIM);
        final var getResult = getServerRequest.getResult().orElseThrow();
        assertEquals(data, getResult);
    }

    @Test
    void readLeafInListWithDefaultParameters() throws Exception {
        final var exampleList = new NodeIdentifier(QName.create(BASE, "exampleList"));
        final var content = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_QNAME))
            .withChild(ImmutableNodes.newUnkeyedListBuilder()
                .withNodeIdentifier(exampleList)
                .withChild(ImmutableNodes.newUnkeyedListEntryBuilder()
                    .withNodeIdentifier(exampleList)
                    .addChild(ImmutableNodes.leafNode(QName.create(BASE, "leafInList"), "I am leaf in list"))
                    .build())
                .build())
            .build();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, CONT_IID);
        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, CONT_IID);

        modulesStrategy().readData(getServerRequest, ContentParam.ALL, CONT_DATA, WithDefaultsParam.TRIM);
        final var getResult = getServerRequest.getResult().orElseThrow();
        assertEquals(content, getResult);
    }
}
