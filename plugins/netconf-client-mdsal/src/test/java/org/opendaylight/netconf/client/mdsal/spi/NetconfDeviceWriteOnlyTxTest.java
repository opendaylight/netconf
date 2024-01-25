/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.Assert.assertEquals;
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
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Commit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.DiscardChanges;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Lock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Unlock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.get.input.Filter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceWriteOnlyTxTest extends AbstractBaseSchemasTest {
    private static final RemoteDeviceId ID = new RemoteDeviceId("test-mount", new InetSocketAddress(99));
    private static final YangInstanceIdentifier STATE = YangInstanceIdentifier.of(NetconfState.QNAME);

    @Mock
    private Rpcs.Normalized rpc;

    @Before
    public void setUp() {
        final var successFuture = Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null));
        doReturn(successFuture, Futures.immediateFailedFuture(new IllegalStateException("Failed tx")), successFuture)
            .when(rpc).invokeNetconf(any(), any());
    }

    @Test
    public void testIgnoreNonVisibleData() {
        final var tx = new WriteCandidateTx(ID, new NetconfBaseOps(rpc, mock(MountPointContext.class)), false, true);
        tx.init();

        final var emptyList = ImmutableNodes.mapNodeBuilder(new NodeIdentifier(Filter.QNAME)).build();
        tx.merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(Filter.QNAME), emptyList);
        tx.put(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(Filter.QNAME), emptyList);

        verify(rpc, atMost(1)).invokeNetconf(any(), any());
    }

    @Test
    public void testDiscardChanges() {
        final var tx = new WriteCandidateTx(ID, new NetconfBaseOps(rpc, mock(MountPointContext.class)), false, true);
        tx.init();
        final var future = tx.commit();
        assertThrows(ExecutionException.class, () -> Futures.getDone(future));

        // verify discard changes was sent
        final var inOrder = inOrder(rpc);
        inOrder.verify(rpc).invokeNetconf(Lock.QNAME, NetconfBaseOps.getLockContent(NETCONF_CANDIDATE_NODEID));
        inOrder.verify(rpc).invokeNetconf(Commit.QNAME, NetconfMessageTransformUtil.COMMIT_RPC_CONTENT);
        inOrder.verify(rpc).invokeNetconf(DiscardChanges.QNAME, DISCARD_CHANGES_RPC_CONTENT);
        inOrder.verify(rpc).invokeNetconf(Unlock.QNAME, NetconfBaseOps.getUnLockContent(NETCONF_CANDIDATE_NODEID));
    }

    @Test
    public void testFailedCommit() {
        doReturn(
            Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null)),
            Futures.immediateFuture(new DefaultDOMRpcResult(RpcResultBuilder.newError(ErrorType.APPLICATION,
                new ErrorTag("a"), "m"))))
            .when(rpc).invokeNetconf(any(), any());

        final var tx = new WriteCandidateTx(ID, new NetconfBaseOps(rpc, mock(MountPointContext.class)), false, true);
        tx.init();

        final var future = tx.commit();
        assertThrows(ExecutionException.class, () -> Futures.getDone(future));
    }

    @Test
    public void testDiscardChangesNotSentWithoutCandidate() {
        doReturn(
            Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null)),
            Futures.immediateFailedFuture(new IllegalStateException("Failed tx")))
            .when(rpc).invokeNetconf(any(), any());

        final var tx = new WriteRunningTx(ID,
            new NetconfBaseOps(rpc, BASE_SCHEMAS.baseSchemaWithNotifications().getMountPointContext()), false, true);
        tx.init();

        tx.delete(LogicalDatastoreType.CONFIGURATION, STATE);
        tx.commit();
        // verify discard changes was sent
        final var inOrder = inOrder(rpc);
        inOrder.verify(rpc).invokeNetconf(Lock.QNAME, NetconfBaseOps.getLockContent(NETCONF_RUNNING_NODEID));
        inOrder.verify(rpc).invokeNetconf(eq(EditConfig.QNAME), any());
        inOrder.verify(rpc).invokeNetconf(Unlock.QNAME, NetconfBaseOps.getUnLockContent(NETCONF_RUNNING_NODEID));
    }

    @Test
    public void testListenerSuccess() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null)))
            .when(rpc).invokeNetconf(any(), any());
        final var tx = new WriteCandidateTx(ID,
            new NetconfBaseOps(rpc, BASE_SCHEMAS.baseSchema().getMountPointContext()), false, true);
        tx.init();

        final var listener = mock(TxListener.class);
        tx.addListener(listener);
        tx.delete(LogicalDatastoreType.CONFIGURATION, STATE);
        tx.commit();
        verify(listener).onTransactionSubmitted(tx);
        verify(listener).onTransactionSuccessful(tx);
        verify(listener, never()).onTransactionFailed(eq(tx), any());
        verify(listener, never()).onTransactionCancelled(tx);
    }

    @Test
    public void testListenerCancellation() throws Exception {
        final var tx = new WriteCandidateTx(ID,
            new NetconfBaseOps(rpc, BASE_SCHEMAS.baseSchema().getMountPointContext()), false, true);
        tx.init();

        final var listener = mock(TxListener.class);
        tx.addListener(listener);
        tx.delete(LogicalDatastoreType.CONFIGURATION, STATE);
        tx.cancel();
        verify(listener).onTransactionCancelled(tx);
        verify(listener, never()).onTransactionSubmitted(tx);
        verify(listener, never()).onTransactionSuccessful(tx);
        verify(listener, never()).onTransactionFailed(eq(tx), any());
    }

    @Test
    public void testListenerFailure() throws Exception {
        final var cause = new IllegalStateException("Failed tx");
        doReturn(Futures.immediateFailedFuture(cause)).when(rpc).invokeNetconf(any(), any());
        final var tx = new WriteCandidateTx(ID,
            new NetconfBaseOps(rpc, BASE_SCHEMAS.baseSchema().getMountPointContext()), false, true);
        tx.init();

        final var listener = mock(TxListener.class);
        tx.addListener(listener);
        tx.delete(LogicalDatastoreType.CONFIGURATION, STATE);
        tx.commit();
        final var excCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onTransactionSubmitted(tx);
        verify(listener).onTransactionFailed(eq(tx), excCaptor.capture());
        assertEquals(cause, excCaptor.getValue().getCause().getCause());
        verify(listener, never()).onTransactionSuccessful(tx);
        verify(listener, never()).onTransactionCancelled(tx);
    }
}
