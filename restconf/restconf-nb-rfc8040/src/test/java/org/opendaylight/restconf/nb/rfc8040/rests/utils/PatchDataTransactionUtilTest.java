/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.CREATE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.DELETE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.MERGE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REMOVE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REPLACE;

import com.google.common.util.concurrent.Futures;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.RestConnectorProvider;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class PatchDataTransactionUtilTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";

    @Mock
    private DOMTransactionChain transactionChain;

    @Mock
    private DOMDataReadWriteTransaction rwTransaction;

    private SchemaContextRef refSchemaCtx;
    private YangInstanceIdentifier instanceIdCreateAndDelete;
    private YangInstanceIdentifier instanceIdMerge;
    private ContainerNode buildBaseContainerForTests;
    private YangInstanceIdentifier targetNodeForCreateAndDelete;
    private YangInstanceIdentifier targetNodeMerge;
    private MapNode buildArtistList;

    // Fields used when delete operation fails to reset transaction chain
    private static Field handler;
    private static Field broker;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        PatchDataTransactionUtilTest.handler = RestConnectorProvider.class.getDeclaredField("transactionChainHandler");
        PatchDataTransactionUtilTest.broker = RestConnectorProvider.class.getDeclaredField("dataBroker");

        PatchDataTransactionUtilTest.handler.setAccessible(true);
        PatchDataTransactionUtilTest.handler.set(RestConnectorProvider.class, mock(TransactionChainHandler.class));

        PatchDataTransactionUtilTest.broker.setAccessible(true);
        PatchDataTransactionUtilTest.broker.set(RestConnectorProvider.class, mock(DOMDataBroker.class));

        this.refSchemaCtx = new SchemaContextRef(
                YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT)));
        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerPlayerQName = QName.create(baseQName, "player");
        final QName leafGapQName = QName.create(baseQName, "gap");
        final QName containerLibraryQName = QName.create(baseQName, "library");
        final QName listArtistQName = QName.create(baseQName, "artist");
        final QName leafNameQName = QName.create(baseQName, "name");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey =
            new YangInstanceIdentifier.NodeIdentifierWithPredicates(listArtistQName, leafNameQName, "name of artist");

        /* instance identifier for accessing leaf node "gap" */
        this.instanceIdCreateAndDelete = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerPlayerQName)
                .node(leafGapQName)
                .build();

        /* values that are used for creating leaf for testPatchDataCreateAndDelete test */
        final LeafNode<?> buildGapLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(leafGapQName))
                .withValue(0.2)
                .build();

        final ContainerNode buildPlayerContainer = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(containerPlayerQName))
                .withChild(buildGapLeaf)
                .build();

        this.buildBaseContainerForTests = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(baseQName))
                .withChild(buildPlayerContainer)
                .build();

        this.targetNodeForCreateAndDelete = YangInstanceIdentifier.builder(this.instanceIdCreateAndDelete)
                .node(containerPlayerQName)
                .node(leafGapQName)
                .build();

        /* instance identifier for accessing leaf node "name" in list "artist" */
        this.instanceIdMerge = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerLibraryQName)
                .node(listArtistQName)
                .nodeWithKey(listArtistQName, QName.create(listArtistQName, "name"), "name of artist")
                .node(leafNameQName)
                .build();

        /* values that are used for creating leaf for testPatchDataReplaceMergeAndRemove test */
        final LeafNode<Object> contentName = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of artist")
                .build();

        final LeafNode<Object> contentDescription = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "description")))
                .withValue("description of artist")
                .build();

        final MapEntryNode mapEntryNode = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(contentName)
                .withChild(contentDescription)
                .build();

        this.buildArtistList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(listArtistQName))
                .withChild(mapEntryNode)
                .build();

        this.targetNodeMerge = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerLibraryQName)
                .node(listArtistQName)
                .nodeWithKey(listArtistQName, leafNameQName, "name of artist")
                .build();

        /* Mocks */
        doReturn(this.rwTransaction).when(this.transactionChain).newReadWriteTransaction();
        doReturn(Futures.immediateCheckedFuture(null)).when(this.rwTransaction).submit();
    }

    @Test
    public void testPatchDataReplaceMergeAndRemove() {
        doReturn(Futures.immediateCheckedFuture(false)).doReturn(Futures.immediateCheckedFuture(true))
                .when(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, this.targetNodeMerge);

        final PatchEntity entityReplace =
                new PatchEntity("edit1", REPLACE, this.targetNodeMerge, this.buildArtistList);
        final PatchEntity entityMerge = new PatchEntity("edit2", MERGE, this.targetNodeMerge, this.buildArtistList);
        final PatchEntity entityRemove = new PatchEntity("edit3", REMOVE, this.targetNodeMerge);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityReplace);
        entities.add(entityMerge);
        entities.add(entityRemove);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.instanceIdMerge, null, null, this.refSchemaCtx.get());
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchRMRm");
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(iidContext, null, this.transactionChain);
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, wrapper, this.refSchemaCtx);

        for (final PatchStatusEntity entity : patchStatusContext.getEditCollection()) {
            assertTrue(entity.isOk());
        }
        assertTrue(patchStatusContext.isOk());
    }

    @Test
    public void testPatchDataCreateAndDelete() throws Exception {
        doReturn(Futures.immediateCheckedFuture(false)).doReturn(Futures.immediateCheckedFuture(true))
                .when(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, this.targetNodeForCreateAndDelete);

        final PatchEntity entityCreate =
                new PatchEntity("edit1", CREATE, this.targetNodeForCreateAndDelete, this.buildBaseContainerForTests);
        final PatchEntity entityDelete =
                new PatchEntity("edit2", DELETE, this.targetNodeForCreateAndDelete);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityCreate);
        entities.add(entityDelete);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.instanceIdCreateAndDelete, null, null, this.refSchemaCtx.get());
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchCD");
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(iidContext, null, this.transactionChain);
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, wrapper, this.refSchemaCtx);

        for (final PatchStatusEntity entity : patchStatusContext.getEditCollection()) {
            assertTrue(entity.isOk());
        }
        assertTrue(patchStatusContext.isOk());
    }

    @Test
    public void deleteNonexistentDataTest() {
        doReturn(Futures.immediateCheckedFuture(false))
                .when(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, this.targetNodeForCreateAndDelete);

        final PatchEntity entityDelete =
                new PatchEntity("edit", DELETE, this.targetNodeForCreateAndDelete);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityDelete);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.instanceIdCreateAndDelete, null, null, this.refSchemaCtx.get());
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchD");
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(iidContext, null, this.transactionChain);
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, wrapper, this.refSchemaCtx);

        assertFalse(patchStatusContext.isOk());
        assertEquals(RestconfError.ErrorType.PROTOCOL,
                patchStatusContext.getEditCollection().get(0).getEditErrors().get(0).getErrorType());
        assertEquals(RestconfError.ErrorTag.DATA_MISSING,
                patchStatusContext.getEditCollection().get(0).getEditErrors().get(0).getErrorTag());
    }

    @Test
    public void testPatchMergePutContainer() throws Exception {
        doReturn(Futures.immediateCheckedFuture(false)).doReturn(Futures.immediateCheckedFuture(true))
                .when(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, this.targetNodeForCreateAndDelete);

        final PatchEntity entityMerge =
                new PatchEntity("edit1", MERGE, this.targetNodeForCreateAndDelete, this.buildBaseContainerForTests);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityMerge);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.instanceIdCreateAndDelete, null, null, this.refSchemaCtx.get());
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchM");
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(iidContext, null, this.transactionChain);
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, wrapper, this.refSchemaCtx);

        for (final PatchStatusEntity entity : patchStatusContext.getEditCollection()) {
            assertTrue(entity.isOk());
        }
        assertTrue(patchStatusContext.isOk());
    }
}