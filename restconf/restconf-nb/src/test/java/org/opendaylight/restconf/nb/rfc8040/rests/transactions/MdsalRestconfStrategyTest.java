/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.util.Optional;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PutDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.DOMException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public final class MdsalRestconfStrategyTest extends AbstractRestconfStrategyTest {
    private static EffectiveModelContext MODULES_SCHEMA;

    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private DOMDataTreeReadTransaction read;

    @BeforeClass
    public static void setupModulesSchema() {
        MODULES_SCHEMA = YangParserTestUtils.parseYangResourceDirectory("/modules");
    }

    @AfterClass
    public static void dropModulesSchema() {
        MODULES_SCHEMA = null;
    }

    @Before
    public void before() {
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
    }

    @Override
    RestconfStrategy testDeleteDataStrategy() {
        // assert that data to delete exists
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateTrueFluentFuture());
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy testNegativeDeleteDataStrategy() {
        // assert that data to delete does NOT exist
        when(readWrite.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of()))
            .thenReturn(immediateFalseFluentFuture());
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy testPostListDataStrategy(final MapEntryNode entryNode, final YangInstanceIdentifier node) {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, entryNode);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy testPostDataFailStrategy(final DOMException domException) {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFailedFluentFuture(domException)).when(readWrite).commit();
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Test
    public void testPutContainerData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        PutDataTransactionUtil.putData(JUKEBOX_IID, EMPTY_JUKEBOX, JUKEBOX_SCHEMA,
            new MdsalRestconfStrategy(mockDataBroker), WriteDataParams.empty());
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
    }

    @Test
    public void testPutLeafData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        PutDataTransactionUtil.putData(GAP_IID, GAP_LEAF, JUKEBOX_SCHEMA, new MdsalRestconfStrategy(mockDataBroker),
            WriteDataParams.empty());
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);
    }


    @Test
    public void testPutListData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        PutDataTransactionUtil.putData(JUKEBOX_IID, JUKEBOX_WITH_BANDS, JUKEBOX_SCHEMA,
            new MdsalRestconfStrategy(mockDataBroker), WriteDataParams.empty());
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS);
    }

    @Override
    RestconfStrategy testPostContainerDataStrategy() {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy testPatchContainerDataStrategy() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy testPatchLeafDataStrategy() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy testPatchListDataStrategy() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy testPatchDataReplaceMergeAndRemoveStrategy() {
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy testPatchDataCreateAndDeleteStrategy() {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, PLAYER_IID);
        doReturn(immediateTrueFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION,
            CREATE_AND_DELETE_TARGET);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy testPatchMergePutContainerStrategy() {
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy deleteNonexistentDataTestStrategy() {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION,
            CREATE_AND_DELETE_TARGET);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    void assertTestDeleteNonexistentData(final PatchStatusContext status) {
        final var editCollection = status.getEditCollection();
        assertEquals(1, editCollection.size());
        final var editErrors = editCollection.get(0).getEditErrors();
        assertEquals(1, editErrors.size());
        final var editError = editErrors.get(0);
        assertEquals(ErrorType.PROTOCOL, editError.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, editError.getErrorTag());
    }

    @Override
    RestconfStrategy readDataConfigTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readAllHavingOnlyConfigTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readAllHavingOnlyNonConfigTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readDataNonConfigTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_2))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_2);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readContainerDataAllTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readContainerDataConfigNoValueOfContentTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(DATA_3))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH);
        doReturn(immediateFluentFuture(Optional.of(DATA_4))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readListDataAllTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(LIST_DATA_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readOrderedListDataAllTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_MAP_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readUnkeyedListDataAllTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, PATH_3);
        doReturn(immediateFluentFuture(Optional.of(UNKEYED_LIST_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, PATH_3);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readLeafListDataAllTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(LEAF_SET_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readOrderedLeafListDataAllTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_1))).when(read)
            .read(LogicalDatastoreType.OPERATIONAL, LEAF_SET_NODE_PATH);
        doReturn(immediateFluentFuture(Optional.of(ORDERED_LEAF_SET_NODE_2))).when(read)
            .read(LogicalDatastoreType.CONFIGURATION, LEAF_SET_NODE_PATH);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Override
    RestconfStrategy readDataWrongPathOrNoContentTestStrategy() {
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.empty())).when(read).read(LogicalDatastoreType.CONFIGURATION, PATH_2);
        return new MdsalRestconfStrategy(mockDataBroker);
    }

    @Test
    public void readLeafWithDefaultParameters() {
        final var data = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(BASE, "exampleLeaf"), "i am leaf"))
            .build();
        final var path = YangInstanceIdentifier.of(CONT_QNAME);
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, path);
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, path);

        assertEquals(data, ReadDataTransactionUtil.readData(ContentParam.ALL, path,
            new MdsalRestconfStrategy(mockDataBroker), WithDefaultsParam.TRIM, MODULES_SCHEMA));
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
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, path);
        doReturn(immediateFluentFuture(Optional.of(data))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, path);

        assertEquals(data, ReadDataTransactionUtil.readData(ContentParam.ALL, path,
            new MdsalRestconfStrategy(mockDataBroker), WithDefaultsParam.TRIM, MODULES_SCHEMA));
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
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, path);
        doReturn(immediateFluentFuture(Optional.of(content))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, path);

        assertEquals(content, ReadDataTransactionUtil.readData(ContentParam.ALL, path,
            new MdsalRestconfStrategy(mockDataBroker), WithDefaultsParam.TRIM, MODULES_SCHEMA));
    }
}
