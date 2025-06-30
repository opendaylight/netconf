/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.ErrorMessage;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.mdsal.spi.DOMServerRpcOperations;
import org.opendaylight.restconf.mdsal.spi.DOMServerStrategy;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.mdsal.MdsalMountPointResolver;
import org.opendaylight.restconf.server.mdsal.MdsalServerStrategy;
import org.opendaylight.restconf.server.spi.CompositeServerStrategy;
import org.opendaylight.restconf.server.spi.ExportingServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerActionOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerMountPointResolver;
import org.opendaylight.restconf.server.spi.NotSupportedServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.restconf.server.spi.ServerStrategy.StrategyAndPath;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.DOMException;

@ExtendWith(MockitoExtension.class)
final class MdsalRestconfStrategyTest extends AbstractServerDataOperationsTest {
    private static final DatabindContext MODULES_DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResourceDirectory("/modules"));
    private static final YangInstanceIdentifier CONT_IID = YangInstanceIdentifier.of(CONT_QNAME);
    private static final Data CONT_DATA = moudlesPath(CONT_IID);

    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMMountPoint mountPoint;
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

    @Override
    ServerDataOperations testDeleteDataStrategy() {
        // assert that data to delete exists
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateTrueFluentFuture());
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testNegativeDeleteDataStrategy() {
        // assert that data to delete does NOT exist
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateFalseFluentFuture());
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testPostListDataStrategy(final MapEntryNode entryNode, final YangInstanceIdentifier node) {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, entryNode);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testPostDataFailStrategy(final DOMException domException) {
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
        // no parent node merge for top level elements
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

    @Override
    ServerDataOperations testPostContainerDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testPatchContainerDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testPatchLeafDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testPatchListDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testPatchDataReplaceMergeAndRemoveStrategy(final MapNode artistList) {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testPatchDataCreateAndDeleteStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, PLAYER_IID);
        doReturn(immediateTrueFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testPatchWithDataExistExceptionStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(immediateTrueFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, PLAYER_IID);
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations testPatchMergePutContainerStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxDataOperations();
    }

    @Override
    ServerDataOperations deleteNonexistentDataTestStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        return jukeboxDataOperations();
    }

    @Override
    void assertTestDeleteNonexistentData(final PatchStatusContext status, final PatchStatusEntity edit) {
        assertNull(status.globalErrors());
        final var editErrors = edit.getEditErrors();
        assertEquals(1, editErrors.size());
        final var editError = editErrors.get(0);
        assertEquals(new ErrorMessage("Data does not exist"), editError.message());
        assertEquals(ErrorType.PROTOCOL, editError.type());
        assertEquals(ErrorTag.DATA_MISSING, editError.tag());
    }

    @Override
    ServerDataOperations readDataConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readAllHavingOnlyConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readDataNonConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readContainerDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readOrderedListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readUnkeyedListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readLeafListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readOrderedLeafListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        return mockDataOperations();
    }

    @Override
    ServerDataOperations readDataWrongPathOrNoContentTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.empty())).when(read).read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        return mockDataOperations();
    }

    @Override
    void assertReadDataWrongPathOrNoContent(final Supplier<NormalizedNode> readResult) {
        final var assertionError = assertThrows(AssertionError.class, readResult::get);
        final var requestException = assertInstanceOf(RequestException.class, assertionError.getCause());
        final var requestError = requestException.errors().getFirst();
        assertNotNull(requestError.message());
        assertEquals("Request could not be completed because the relevant data model content does not exist",
            requestError.message().elementBody());
    }

    @Override
    NormalizedNode readData(final ContentParam content, Data path,
            final ServerDataOperations dataOperations) {
        if (dataOperations instanceof MdsalRestconfStrategy mdsalRestconfStrategy) {
            try {
                mdsalRestconfStrategy.readData(getServerRequest, content, path, null);
                return getServerRequest.getResult().orElse(null);
            } catch (TimeoutException | InterruptedException | RequestException e) {
                throw new AssertionError(e);
            }
        } else {
            fail("Wrong ServerDataOperations type" + dataOperations);
            return null;
        }
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

    @Test
    void testGetRestconfStrategyLocal() throws Exception {
        final var strategy = jukeboxStrategy();
        assertEquals(new StrategyAndPath(strategy, ApiPath.empty()), strategy.resolveStrategy(ApiPath.empty()));
    }

    @Test
    void testGetRestconfStrategyMountDataBroker() throws Exception {
        doReturn(Optional.empty()).when(mountPoint).getService(DOMServerStrategy.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of());

        final var root = jukeboxStrategy();
        final var strategyAndPath = root.resolveStrategy(ApiPath.parse("yang-ext:mount"));
        assertEquals(ApiPath.empty(), strategyAndPath.path());
        final var strategy = assertInstanceOf(MdsalServerStrategy.class, strategyAndPath.strategy());
        assertNotSame(root, strategy);
        assertInstanceOf(MdsalRestconfStrategy.class, strategy.data());
    }

    @Test
    void testGetRestconfStrategyFromMountPointDOMServerStrategy() throws Exception {
        // Prepare DOMServerStrategy instance.
        final var databindContext = DatabindContext.ofModel(JUKEBOX_SCHEMA);
        final var mdsalRestconfStrategy = new MdsalRestconfStrategy(databindContext, dataBroker);
        final var compositeServerStrategy = new CompositeServerStrategy(databindContext,
            NotSupportedServerMountPointResolver.INSTANCE, NotSupportedServerActionOperations.INSTANCE,
            mdsalRestconfStrategy, new ExportingServerModulesOperations(JUKEBOX_SCHEMA),
            NotSupportedServerRpcOperations.INSTANCE);
        final var domServerStrategy = new DOMServerStrategy(compositeServerStrategy);

        // Prepare environment.
        doReturn(Optional.of(domServerStrategy)).when(mountPoint).getService(DOMServerStrategy.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of());

        // Resolve strategy for mountPoint.
        final var strategyAndPath = jukeboxStrategy().resolveStrategy(ApiPath.parse("yang-ext:mount"));

        // Verify provided strategy.
        assertEquals(ApiPath.empty(), strategyAndPath.path());
        final var strategy = assertInstanceOf(CompositeServerStrategy.class, strategyAndPath.strategy());
        assertInstanceOf(MdsalRestconfStrategy.class, strategy.data());
    }

    @Test
    void testGetRestconfStrategyMountNone() throws Exception {
        doReturn(Optional.empty()).when(mountPoint).getService(DOMServerStrategy.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of());

        final var strategy = jukeboxStrategy();
        final var mountPath = ApiPath.parse("yang-ext:mount");

        final var strategyAndPath = strategy.resolveStrategy(mountPath);
        assertEquals(ApiPath.empty(), strategyAndPath.path());

        final var request = new CompletingServerRequest<DataGetResult>();
        strategyAndPath.strategy().dataGET(request);

        final var errors = assertThrows(RequestException.class, request::getResult).errors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, error.tag());
        assertEquals(new ErrorMessage("Data request not supported"), error.message());
        final var errorPath = error.path();
        assertNotNull(errorPath);
        assertEquals(YangInstanceIdentifier.of(), errorPath.path());
    }

    private MdsalServerStrategy jukeboxStrategy() {
        return new MdsalServerStrategy(JUKEBOX_DATABIND, new MdsalMountPointResolver(mountPointService),
            NotSupportedServerActionOperations.INSTANCE, new MdsalRestconfStrategy(JUKEBOX_DATABIND, dataBroker),
            NotSupportedServerModulesOperations.INSTANCE, new DOMServerRpcOperations(rpcService));
    }
}
