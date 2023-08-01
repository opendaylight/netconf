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
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
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
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PatchDataTransactionUtilTest extends AbstractJukeboxTest {
    // instance identifier for accessing container node "player"
    private static final YangInstanceIdentifier PLAYER_IID = YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYER_QNAME);
    private static final YangInstanceIdentifier ARTIST_IID = YangInstanceIdentifier.builder()
        .node(JUKEBOX_QNAME)
        .node(LIBRARY_QNAME)
        .node(ARTIST_QNAME)
        .nodeWithKey(ARTIST_QNAME, NAME_QNAME, "name of artist")
        .build();
    // FIXME: this looks weird
    private static final YangInstanceIdentifier CREATE_AND_DELETE_TARGET = GAP_IID.node(PLAYER_QNAME).node(GAP_QNAME);

    @Mock
    private DOMDataTreeReadWriteTransaction rwTransaction;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    @Before
    public void before() {
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
        final var buildArtistList = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(ARTIST_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(ARTIST_QNAME, NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "description of artist"))
                .build())
            .build();

        final var patchContext = new PatchContext(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, ARTIST_IID.node(NAME_QNAME)),
            List.of(new PatchEntity("edit1", REPLACE, ARTIST_IID, buildArtistList),
                new PatchEntity("edit2", MERGE, ARTIST_IID, buildArtistList),
                new PatchEntity("edit3", REMOVE, ARTIST_IID)),
            "patchRMRm");

        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .remove(LogicalDatastoreType.CONFIGURATION, ARTIST_IID);

        patch(patchContext, new MdsalRestconfStrategy(mockDataBroker), false);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), false);
    }

    @Test
    public void testPatchDataCreateAndDelete() {
        doReturn(immediateFalseFluentFuture()).when(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            PLAYER_IID);
        doReturn(immediateTrueFluentFuture()).when(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            CREATE_AND_DELETE_TARGET);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(LogicalDatastoreType.CONFIGURATION, PLAYER_IID, EMPTY_JUKEBOX, Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, CREATE_AND_DELETE_TARGET);

        final var patchContext = new PatchContext(InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID),
            List.of(new PatchEntity("edit1", CREATE, PLAYER_IID, EMPTY_JUKEBOX),
                new PatchEntity("edit2", DELETE, CREATE_AND_DELETE_TARGET)),
            "patchCD");
        patch(patchContext, new MdsalRestconfStrategy(mockDataBroker), true);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), true);
    }

    @Test
    public void deleteNonexistentDataTest() {
        doReturn(immediateFalseFluentFuture()).when(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            CREATE_AND_DELETE_TARGET);
        doReturn(Futures.immediateFailedFuture(
            new TransactionCommitFailedException("Commit of transaction " + this + " failed",
                new NetconfDocumentedException("id", ErrorType.RPC, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR))))
            .when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, CREATE_AND_DELETE_TARGET);

        final var patchContext = new PatchContext(InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID),
            List.of(new PatchEntity("edit", DELETE, CREATE_AND_DELETE_TARGET)), "patchD");
        deleteMdsal(patchContext, new MdsalRestconfStrategy(mockDataBroker));
        deleteNetconf(patchContext, new NetconfRestconfStrategy(netconfService));
    }

    @Test
    public void testPatchMergePutContainer() {
        final var patchContext = new PatchContext(InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID),
            List.of(new PatchEntity("edit1", MERGE, PLAYER_IID, EMPTY_JUKEBOX)), "patchM");
        patch(patchContext, new MdsalRestconfStrategy(mockDataBroker), false);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), false);
    }

    private static void patch(final PatchContext patchContext, final RestconfStrategy strategy, final boolean failed) {
        final var patchStatusContext = PatchDataTransactionUtil.patchData(patchContext, strategy, JUKEBOX_SCHEMA);
        for (var entity : patchStatusContext.getEditCollection()) {
            if (failed) {
                assertTrue("Edit " + entity.getEditId() + " failed", entity.isOk());
            } else {
                assertTrue(entity.isOk());
            }
        }
        assertTrue(patchStatusContext.isOk());
    }

    private static void deleteMdsal(final PatchContext patchContext, final RestconfStrategy strategy) {
        final var patchStatusContext = PatchDataTransactionUtil.patchData(patchContext, strategy, JUKEBOX_SCHEMA);
        assertFalse(patchStatusContext.isOk());

        final var editCollection = patchStatusContext.getEditCollection();
        assertEquals(1, editCollection.size());
        final var editErrors = patchStatusContext.getEditCollection().get(0).getEditErrors();
        assertEquals(1, editErrors.size());
        final var editError = editErrors.get(0);
        assertEquals(ErrorType.PROTOCOL, editError.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, editError.getErrorTag());
    }

    private static void deleteNetconf(final PatchContext patchContext, final RestconfStrategy strategy) {
        final var patchStatusContext = PatchDataTransactionUtil.patchData(patchContext, strategy, JUKEBOX_SCHEMA);
        assertFalse(patchStatusContext.isOk());
        final var globalErrors = patchStatusContext.getGlobalErrors();
        assertEquals(1, globalErrors.size());
        final var globalError = globalErrors.get(0);
        assertEquals(ErrorType.PROTOCOL, globalError.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, globalError.getErrorTag());
    }
}
