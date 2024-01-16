/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
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
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy.StrategyAndTail;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.DOMException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public final class MdsalRestconfStrategyTest extends AbstractRestconfStrategyTest {
    private static final DatabindContext MODULES_DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResourceDirectory("/modules"));

    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMMountPoint mountPoint;
    @Mock
    private NetconfDataTreeService netconfService;

    @Before
    public void before() {
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
    }

    @Override
    RestconfStrategy newStrategy(final DatabindContext databind) {
        return new MdsalRestconfStrategy(databind, dataBroker, rpcService, null, null, mountPointService);
    }

    private @NonNull RestconfStrategy modulesStrategy() {
        return newStrategy(MODULES_DATABIND);
    }

    @Override
    RestconfStrategy testDeleteDataStrategy() {
        // assert that data to delete exists
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateTrueFluentFuture());
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testNegativeDeleteDataStrategy() {
        // assert that data to delete does NOT exist
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateFalseFluentFuture());
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPostListDataStrategy(final MapEntryNode entryNode, final YangInstanceIdentifier node) {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, entryNode);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPostDataFailStrategy(final DOMException domException) {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFailedFluentFuture(domException)).when(readWrite).commit();
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        return jukeboxStrategy();
    }

    @Test
    public void testPutContainerData() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        jukeboxStrategy().putData(JUKEBOX_IID, EMPTY_JUKEBOX, null);
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
    }

    @Test
    public void testPutLeafData() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        jukeboxStrategy().putData(GAP_IID, GAP_LEAF, null);
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);
    }


    @Test
    public void testPutListData() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        jukeboxStrategy().putData(JUKEBOX_IID, JUKEBOX_WITH_BANDS, null);
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS);
    }

    @Override
    RestconfStrategy testPostContainerDataStrategy() {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchContainerDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchLeafDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchListDataStrategy() {
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchDataReplaceMergeAndRemoveStrategy() {
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchDataCreateAndDeleteStrategy() {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, PLAYER_IID);
        doReturn(immediateTrueFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION,
            CREATE_AND_DELETE_TARGET);
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy testPatchMergePutContainerStrategy() {
        return jukeboxStrategy();
    }

    @Override
    RestconfStrategy deleteNonexistentDataTestStrategy() {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION,
            CREATE_AND_DELETE_TARGET);
        return jukeboxStrategy();
    }

    @Override
    void assertTestDeleteNonexistentData(final PatchStatusContext status, final PatchStatusEntity edit) {
        assertNull(status.globalErrors());
        final var editErrors = edit.getEditErrors();
        assertEquals(1, editErrors.size());
        final var editError = editErrors.get(0);
        assertEquals("Data does not exist", editError.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, editError.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, editError.getErrorTag());
    }

    @Override
    RestconfStrategy readDataConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readAllHavingOnlyConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readDataNonConfigTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readContainerDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readOrderedListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readUnkeyedListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readLeafListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readOrderedLeafListDataAllTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        return mockStrategy();
    }

    @Override
    RestconfStrategy readDataWrongPathOrNoContentTestStrategy() {
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.empty())).when(read).read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        return mockStrategy();
    }

    @Test
    public void readLeafWithDefaultParameters() {
        final var data = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(BASE, "exampleLeaf"), "i am leaf"))
            .build();
        final var path = YangInstanceIdentifier.of(CONT_QNAME);
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, path);
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, path);

        assertEquals(data, modulesStrategy().readData(ContentParam.ALL, path, WithDefaultsParam.TRIM));
    }

    @Test
    public void readContainerWithDefaultParameters() {
        final var exampleList = new NodeIdentifier(QName.create(BASE, "exampleList"));
        final var data = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_QNAME))
            .withChild(Builders.unkeyedListBuilder()
                .withNodeIdentifier(exampleList)
                .withChild(Builders.unkeyedListEntryBuilder()
                    .withNodeIdentifier(exampleList)
                    .withChild(Builders.containerBuilder()
                        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "containerBool")))
                        .withChild(ImmutableNodes.leafNode(QName.create(BASE, "leafBool"), true))
                        .build())
                    .addChild(Builders.containerBuilder()
                        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "containerInt")))
                        .withChild(ImmutableNodes.leafNode(QName.create(BASE, "leafInt"), 12))
                        .build())
                    .build())
                .build())
            .build();
        final var path = YangInstanceIdentifier.of(CONT_QNAME);
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, path);
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, path);

        assertEquals(data, modulesStrategy().readData(ContentParam.ALL, path, WithDefaultsParam.TRIM));
    }

    @Test
    public void readLeafInListWithDefaultParameters() {
        final var exampleList = new NodeIdentifier(QName.create(BASE, "exampleList"));
        final var content = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_QNAME))
            .withChild(Builders.unkeyedListBuilder()
                .withNodeIdentifier(exampleList)
                .withChild(Builders.unkeyedListEntryBuilder()
                    .withNodeIdentifier(exampleList)
                    .addChild(ImmutableNodes.leafNode(QName.create(BASE, "leafInList"), "I am leaf in list"))
                    .build())
                .build())
            .build();
        final var path = YangInstanceIdentifier.of(CONT_QNAME);
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, path);
        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, path);

        assertEquals(content, modulesStrategy().readData(ContentParam.ALL, path, WithDefaultsParam.TRIM));
    }

    @Test
    public void testGetRestconfStrategyLocal() {
        final var strategy = jukeboxStrategy();
        assertEquals(new StrategyAndTail(strategy, ApiPath.empty()), strategy.resolveStrategy(ApiPath.empty()));
    }

    @Test
    public void testGetRestconfStrategyMountDataBroker() throws Exception {
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of());

        final var strategy = jukeboxStrategy();
        final var result = strategy.resolveStrategy(ApiPath.parse("yang-ext:mount"));
        assertEquals(ApiPath.empty(), result.tail());
        assertNotSame(strategy, assertInstanceOf(MdsalRestconfStrategy.class, result.strategy()));
    }

    @Test
    public void testGetRestconfStrategyMountNetconfService() throws Exception {
        doReturn(Optional.of(netconfService)).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of());

        final var strategy = jukeboxStrategy();
        final var result = strategy.resolveStrategy(ApiPath.parse("yang-ext:mount"));
        assertEquals(ApiPath.empty(), result.tail());
        assertInstanceOf(NetconfRestconfStrategy.class, result.strategy());
    }

    @Test
    public void testGetRestconfStrategyMountNone() throws Exception {
        doReturn(JUKEBOX_IID).when(mountPoint).getIdentifier();
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of());

        final var strategy = jukeboxStrategy();
        final var mountPath = ApiPath.parse("yang-ext:mount");

        final var ex = assertThrows(RestconfDocumentedException.class, () -> strategy.resolveStrategy(mountPath));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
        assertEquals("Could not find a supported access interface in mount point", error.getErrorMessage());
        assertEquals(JUKEBOX_IID, error.getErrorPath());
    }
}
