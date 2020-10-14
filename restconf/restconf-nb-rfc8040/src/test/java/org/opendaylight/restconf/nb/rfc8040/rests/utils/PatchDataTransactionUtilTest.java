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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.CREATE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.DELETE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.MERGE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REMOVE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REPLACE;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PatchDataTransactionUtilTest {
    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";
    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private DOMDataTreeReadWriteTransaction rwTransaction;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    private TransactionChainHandler transactionChainHandler;
    private EffectiveModelContext refSchemaCtx;
    private YangInstanceIdentifier instanceIdContainer;
    private YangInstanceIdentifier instanceIdCreateAndDelete;
    private YangInstanceIdentifier instanceIdMerge;
    private ContainerNode buildBaseContainerForTests;
    private YangInstanceIdentifier targetNodeForCreateAndDelete;
    private YangInstanceIdentifier targetNodeMerge;
    private MapNode buildArtistList;

    @Before
    public void setUp() throws Exception {
        doReturn(transactionChain).when(mockDataBroker).createTransactionChain(any());
        transactionChainHandler = new TransactionChainHandler(mockDataBroker);

        this.refSchemaCtx = YangParserTestUtils.parseYangFiles(
            TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));
        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerPlayerQName = QName.create(baseQName, "player");
        final QName leafGapQName = QName.create(baseQName, "gap");
        final QName containerLibraryQName = QName.create(baseQName, "library");
        final QName listArtistQName = QName.create(baseQName, "artist");
        final QName leafNameQName = QName.create(baseQName, "name");
        final NodeIdentifierWithPredicates nodeWithKey = NodeIdentifierWithPredicates.of(listArtistQName, leafNameQName,
            "name of artist");

        /* instance identifier for accessing container node "player" */
        this.instanceIdContainer = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerPlayerQName)
                .build();

        /* instance identifier for accessing leaf node "gap" */
        this.instanceIdCreateAndDelete = instanceIdContainer.node(leafGapQName);

        /* values that are used for creating leaf for testPatchDataCreateAndDelete test */
        final LeafNode<?> buildGapLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(leafGapQName))
                .withValue(0.2)
                .build();

        final ContainerNode buildPlayerContainer = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(containerPlayerQName))
                .withChild(buildGapLeaf)
                .build();

        this.buildBaseContainerForTests = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(baseQName))
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
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of artist")
                .build();

        final LeafNode<Object> contentDescription = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "description")))
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
        doReturn(CommitInfo.emptyFluentFuture()).when(this.rwTransaction).commit();
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());
    }

    @Test
    public void testPatchDataReplaceMergeAndRemove() {
        final PatchEntity entityReplace =
                new PatchEntity("edit1", REPLACE, this.targetNodeMerge, this.buildArtistList);
        final PatchEntity entityMerge = new PatchEntity("edit2", MERGE, this.targetNodeMerge, this.buildArtistList);
        final PatchEntity entityRemove = new PatchEntity("edit3", REMOVE, this.targetNodeMerge);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityReplace);
        entities.add(entityMerge);
        entities.add(entityRemove);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.instanceIdMerge, null, null, this.refSchemaCtx);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchRMRm");

        patch(patchContext, new MdsalRestconfStrategy(transactionChainHandler), false);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), false);
    }

    @Test
    public void testPatchDataCreateAndDelete() {
        doReturn(immediateFalseFluentFuture()).when(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            this.instanceIdContainer);
        doReturn(immediateTrueFluentFuture()).when(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            this.targetNodeForCreateAndDelete);

        final PatchEntity entityCreate =
                new PatchEntity("edit1", CREATE, this.instanceIdContainer, this.buildBaseContainerForTests);
        final PatchEntity entityDelete =
                new PatchEntity("edit2", DELETE, this.targetNodeForCreateAndDelete);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityCreate);
        entities.add(entityDelete);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.instanceIdCreateAndDelete, null, null, this.refSchemaCtx);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchCD");
        patch(patchContext, new MdsalRestconfStrategy(transactionChainHandler), true);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), true);
    }

    @Test
    public void deleteNonexistentDataTest() {
        doReturn(immediateFalseFluentFuture()).when(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            this.targetNodeForCreateAndDelete);
        final NetconfDocumentedException exception = new NetconfDocumentedException("id",
            DocumentedException.ErrorType.PROTOCOL, DocumentedException.ErrorTag.from("data-missing"),
            DocumentedException.ErrorSeverity.ERROR);
        final SettableFuture<? extends CommitInfo> ret = SettableFuture.create();
        ret.setException(new TransactionCommitFailedException(
            String.format("Commit of transaction %s failed", this), exception));

        Mockito.when(this.netconfService.commit(any())).thenAnswer(invocation -> ret);

        final PatchEntity entityDelete = new PatchEntity("edit", DELETE, this.targetNodeForCreateAndDelete);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityDelete);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.instanceIdCreateAndDelete, null, null, this.refSchemaCtx);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchD");
        deleteMdsal(patchContext, new MdsalRestconfStrategy(transactionChainHandler));
        deleteNetconf(patchContext, new NetconfRestconfStrategy(netconfService));
    }

    @Test
    public void testPatchMergePutContainer() {
        final PatchEntity entityMerge =
                new PatchEntity("edit1", MERGE, this.instanceIdContainer, this.buildBaseContainerForTests);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityMerge);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.instanceIdCreateAndDelete, null, null, this.refSchemaCtx);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchM");
        patch(patchContext, new MdsalRestconfStrategy(transactionChainHandler), false);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), false);
    }

    private void patch(final PatchContext patchContext, final RestconfStrategy strategy,
                       final boolean failed) {
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, strategy, this.refSchemaCtx);
        for (final PatchStatusEntity entity : patchStatusContext.getEditCollection()) {
            if (failed) {
                assertTrue("Edit " + entity.getEditId() + " failed", entity.isOk());
            } else {
                assertTrue(entity.isOk());
            }
        }
        assertTrue(patchStatusContext.isOk());
    }

    private void deleteMdsal(final PatchContext patchContext, final RestconfStrategy strategy) {
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, strategy, this.refSchemaCtx);

        assertFalse(patchStatusContext.isOk());
        assertEquals(RestconfError.ErrorType.PROTOCOL,
                patchStatusContext.getEditCollection().get(0).getEditErrors().get(0).getErrorType());
        assertEquals(RestconfError.ErrorTag.DATA_MISSING,
                patchStatusContext.getEditCollection().get(0).getEditErrors().get(0).getErrorTag());
    }

    private void deleteNetconf(PatchContext patchContext, RestconfStrategy strategy) {
        final PatchStatusContext patchStatusContext =
            PatchDataTransactionUtil.patchData(patchContext, strategy, this.refSchemaCtx);

        assertFalse(patchStatusContext.isOk());
        assertEquals(RestconfError.ErrorType.PROTOCOL,
            patchStatusContext.getGlobalErrors().get(0).getErrorType());
        assertEquals(RestconfError.ErrorTag.DATA_MISSING,
            patchStatusContext.getGlobalErrors().get(0).getErrorTag());
    }
}
