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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PatchDataTransactionUtilTest extends AbstractJukeboxTest {
    @Mock
    private DOMDataTreeReadWriteTransaction rwTransaction;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    private YangInstanceIdentifier instanceIdContainer;
    private YangInstanceIdentifier instanceIdCreateAndDelete;
    private YangInstanceIdentifier instanceIdMerge;
    private ContainerNode buildBaseContainerForTests;
    private YangInstanceIdentifier targetNodeForCreateAndDelete;
    private YangInstanceIdentifier targetNodeMerge;
    private MapNode buildArtistList;

    @Before
    public void setUp() {
        // instance identifier for accessing container node "player"
        instanceIdContainer = YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYER_QNAME);

        // instance identifier for accessing leaf node "gap"
        instanceIdCreateAndDelete = instanceIdContainer.node(GAP_QNAME);

        // values that are used for creating leaf for testPatchDataCreateAndDelete test
        buildBaseContainerForTests = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(PLAYER_QNAME))
                .withChild(ImmutableNodes.leafNode(GAP_QNAME, 0.2))
                .build())
            .build();

        targetNodeForCreateAndDelete = instanceIdCreateAndDelete.node(PLAYER_QNAME).node(GAP_QNAME);

        // instance identifier for accessing leaf node "name" in list "artist"
        instanceIdMerge = YangInstanceIdentifier.builder()
                .node(JUKEBOX_QNAME)
                .node(LIBRARY_QNAME)
                .node(ARTIST_QNAME)
                .nodeWithKey(ARTIST_QNAME, NAME_QNAME, "name of artist")
                .node(NAME_QNAME)
                .build();

        // values that are used for creating leaf for testPatchDataReplaceMergeAndRemove test
        buildArtistList = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(ARTIST_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(ARTIST_QNAME, NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "description of artist"))
                .build())
            .build();

        targetNodeMerge = YangInstanceIdentifier.builder()
                .node(JUKEBOX_QNAME)
                .node(LIBRARY_QNAME)
                .node(ARTIST_QNAME)
                .nodeWithKey(ARTIST_QNAME, NAME_QNAME, "name of artist")
                .build();

        /* Mocks */
        doReturn(rwTransaction).when(mockDataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(rwTransaction).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).discardChanges();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).merge(any(), any(),
            any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).replace(any(), any(),
            any(), any());
    }

    @Test
    public void testPatchDataReplaceMergeAndRemove() {
        final PatchEntity entityReplace =
                new PatchEntity("edit1", REPLACE, targetNodeMerge, buildArtistList);
        final PatchEntity entityMerge = new PatchEntity("edit2", MERGE, targetNodeMerge, buildArtistList);
        final PatchEntity entityRemove = new PatchEntity("edit3", REMOVE, targetNodeMerge);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityReplace);
        entities.add(entityMerge);
        entities.add(entityRemove);

        final InstanceIdentifierContext iidContext =
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, instanceIdMerge);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchRMRm");

        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .remove(LogicalDatastoreType.CONFIGURATION, targetNodeMerge);

        patch(patchContext, new MdsalRestconfStrategy(mockDataBroker), false);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), false);
    }

    @Test
    public void testPatchDataCreateAndDelete() {
        doReturn(immediateFalseFluentFuture()).when(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            instanceIdContainer);
        doReturn(immediateTrueFluentFuture()).when(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            targetNodeForCreateAndDelete);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(LogicalDatastoreType.CONFIGURATION, instanceIdContainer, buildBaseContainerForTests,
                Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, targetNodeForCreateAndDelete);

        final PatchEntity entityCreate =
                new PatchEntity("edit1", CREATE, instanceIdContainer, buildBaseContainerForTests);
        final PatchEntity entityDelete =
                new PatchEntity("edit2", DELETE, targetNodeForCreateAndDelete);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityCreate);
        entities.add(entityDelete);

        final InstanceIdentifierContext iidContext =
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, instanceIdCreateAndDelete);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchCD");
        patch(patchContext, new MdsalRestconfStrategy(mockDataBroker), true);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), true);
    }

    @Test
    public void deleteNonexistentDataTest() {
        doReturn(immediateFalseFluentFuture()).when(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            targetNodeForCreateAndDelete);
        final NetconfDocumentedException exception = new NetconfDocumentedException("id",
            ErrorType.RPC, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR);
        final SettableFuture<? extends DOMRpcResult> ret = SettableFuture.create();
        ret.setException(new TransactionCommitFailedException(
            String.format("Commit of transaction %s failed", this), exception));

        doReturn(ret).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, targetNodeForCreateAndDelete);

        final PatchEntity entityDelete = new PatchEntity("edit", DELETE, targetNodeForCreateAndDelete);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityDelete);

        final PatchContext patchContext = new PatchContext(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, instanceIdCreateAndDelete), entities, "patchD");
        deleteMdsal(patchContext, new MdsalRestconfStrategy(mockDataBroker));
        deleteNetconf(patchContext, new NetconfRestconfStrategy(netconfService));
    }

    @Test
    public void testPatchMergePutContainer() {
        final PatchEntity entityMerge =
                new PatchEntity("edit1", MERGE, instanceIdContainer, buildBaseContainerForTests);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityMerge);

        final InstanceIdentifierContext iidContext =
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, instanceIdCreateAndDelete);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchM");
        patch(patchContext, new MdsalRestconfStrategy(mockDataBroker), false);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), false);
    }

    private static void patch(final PatchContext patchContext, final RestconfStrategy strategy, final boolean failed) {
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, strategy, JUKEBOX_SCHEMA);
        for (final PatchStatusEntity entity : patchStatusContext.getEditCollection()) {
            if (failed) {
                assertTrue("Edit " + entity.getEditId() + " failed", entity.isOk());
            } else {
                assertTrue(entity.isOk());
            }
        }
        assertTrue(patchStatusContext.isOk());
    }

    private static void deleteMdsal(final PatchContext patchContext, final RestconfStrategy strategy) {
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, strategy, JUKEBOX_SCHEMA);

        assertFalse(patchStatusContext.isOk());
        assertEquals(ErrorType.PROTOCOL,
                patchStatusContext.getEditCollection().get(0).getEditErrors().get(0).getErrorType());
        assertEquals(ErrorTag.DATA_MISSING,
                patchStatusContext.getEditCollection().get(0).getEditErrors().get(0).getErrorTag());
    }

    private static void deleteNetconf(final PatchContext patchContext, final RestconfStrategy strategy) {
        final PatchStatusContext patchStatusContext = PatchDataTransactionUtil.patchData(patchContext, strategy,
            JUKEBOX_SCHEMA);

        assertFalse(patchStatusContext.isOk());
        assertEquals(ErrorType.PROTOCOL,
            patchStatusContext.getGlobalErrors().get(0).getErrorType());
        assertEquals(ErrorTag.DATA_MISSING,
            patchStatusContext.getGlobalErrors().get(0).getErrorTag());
    }
}
