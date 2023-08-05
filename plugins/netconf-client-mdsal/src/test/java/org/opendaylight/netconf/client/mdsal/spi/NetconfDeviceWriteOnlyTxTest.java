/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.DISCARD_CHANGES_RPC_CONTENT;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_FILTER_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceWriteOnlyTxTest extends AbstractBaseSchemasTest {
    private final RemoteDeviceId id = new RemoteDeviceId("test-mount", new InetSocketAddress(99));

    @Mock
    private Rpcs.Normalized rpc;
    private YangInstanceIdentifier yangIId;

    @Before
    public void setUp() {
        final ListenableFuture<DefaultDOMRpcResult> successFuture =
                Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null));

        doReturn(successFuture)
                .doReturn(Futures.immediateFailedFuture(new IllegalStateException("Failed tx")))
                .doReturn(successFuture)
                .when(rpc).invokeNetconf(any(), any());

        yangIId = YangInstanceIdentifier.builder().node(NetconfState.QNAME).build();
    }

    @Test
    public void testIgnoreNonVisibleData() {
        final WriteCandidateTx tx = new WriteCandidateTx(id, new NetconfBaseOps(rpc, mock(MountPointContext.class)),
                false);
        final MapNode emptyList = ImmutableNodes.mapNodeBuilder(NETCONF_FILTER_QNAME).build();
        tx.merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(NETCONF_FILTER_QNAME), emptyList);
        tx.put(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(NETCONF_FILTER_QNAME), emptyList);

        verify(rpc, atMost(1)).invokeNetconf(any(), any());
    }

    @Test
    public void testDiscardChanges() {
        final var future = new WriteCandidateTx(id, new NetconfBaseOps(rpc, mock(MountPointContext.class)), false)
            .commit();
        assertThrows(ExecutionException.class, () -> Futures.getDone(future));

        // verify discard changes was sent
        final InOrder inOrder = inOrder(rpc);
        inOrder.verify(rpc).invokeNetconf(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME,
            NetconfBaseOps.getLockContent(NETCONF_CANDIDATE_NODEID));
        inOrder.verify(rpc).invokeNetconf(NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME,
            NetconfMessageTransformUtil.COMMIT_RPC_CONTENT);
        inOrder.verify(rpc).invokeNetconf(NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME,
            DISCARD_CHANGES_RPC_CONTENT);
        inOrder.verify(rpc).invokeNetconf(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME,
            NetconfBaseOps.getUnLockContent(NETCONF_CANDIDATE_NODEID));
    }

    @Test
    public void testFailedCommit() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null)))
            .doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(RpcResultBuilder.newError(ErrorType.APPLICATION,
                new ErrorTag("a"), "m")))).when(rpc).invokeNetconf(any(), any());

        final var future = new WriteCandidateTx(id, new NetconfBaseOps(rpc, mock(MountPointContext.class)), false)
            .commit();

        assertThrows(ExecutionException.class, () -> Futures.getDone(future));
    }

    @Test
    public void testDiscardChangesNotSentWithoutCandidate() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null)))
            .doReturn(Futures.immediateFailedFuture(new IllegalStateException("Failed tx")))
            .when(rpc).invokeNetconf(any(), any());

        final WriteRunningTx tx = new WriteRunningTx(id,
            new NetconfBaseOps(rpc, BASE_SCHEMAS.getBaseSchemaWithNotifications().getMountPointContext()), false);

        tx.delete(LogicalDatastoreType.CONFIGURATION, yangIId);
        tx.commit();
        // verify discard changes was sent
        final InOrder inOrder = inOrder(rpc);
        inOrder.verify(rpc).invokeNetconf(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME,
                NetconfBaseOps.getLockContent(NETCONF_RUNNING_NODEID));
        inOrder.verify(rpc).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), any());
        inOrder.verify(rpc).invokeNetconf(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME,
                NetconfBaseOps.getUnLockContent(NETCONF_RUNNING_NODEID));
    }

    @Test
    public void testListenerSuccess() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null)))
                .when(rpc).invokeNetconf(any(), any());
        final WriteCandidateTx tx = new WriteCandidateTx(
                id, new NetconfBaseOps(rpc, BASE_SCHEMAS.getBaseSchema().getMountPointContext()), false);
        final TxListener listener = mock(TxListener.class);
        tx.addListener(listener);
        tx.delete(LogicalDatastoreType.CONFIGURATION, yangIId);
        tx.commit();
        verify(listener).onTransactionSubmitted(tx);
        verify(listener).onTransactionSuccessful(tx);
        verify(listener, never()).onTransactionFailed(eq(tx), any());
        verify(listener, never()).onTransactionCancelled(tx);
    }

    @Test
    public void testListenerCancellation() throws Exception {
        final WriteCandidateTx tx = new WriteCandidateTx(
                id, new NetconfBaseOps(rpc, BASE_SCHEMAS.getBaseSchema().getMountPointContext()), false);
        final TxListener listener = mock(TxListener.class);
        tx.addListener(listener);
        tx.delete(LogicalDatastoreType.CONFIGURATION, yangIId);
        tx.cancel();
        verify(listener).onTransactionCancelled(tx);
        verify(listener, never()).onTransactionSubmitted(tx);
        verify(listener, never()).onTransactionSuccessful(tx);
        verify(listener, never()).onTransactionFailed(eq(tx), any());
    }

    @Test
    public void testListenerFailure() throws Exception {
        final IllegalStateException cause = new IllegalStateException("Failed tx");
        doReturn(Futures.immediateFailedFuture(cause)).when(rpc).invokeNetconf(any(), any());
        final WriteCandidateTx tx = new WriteCandidateTx(
                id, new NetconfBaseOps(rpc, BASE_SCHEMAS.getBaseSchema().getMountPointContext()), false);
        final TxListener listener = mock(TxListener.class);
        tx.addListener(listener);
        tx.delete(LogicalDatastoreType.CONFIGURATION, yangIId);
        tx.commit();
        final ArgumentCaptor<Exception> excCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onTransactionSubmitted(tx);
        verify(listener).onTransactionFailed(eq(tx), excCaptor.capture());
        Assert.assertEquals(cause, excCaptor.getValue().getCause().getCause());
        verify(listener, never()).onTransactionSuccessful(tx);
        verify(listener, never()).onTransactionCancelled(tx);
    }
}
