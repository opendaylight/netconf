/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.databind.DatabindContext;
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
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class NetconfDeviceWriteOnlyTxTest extends AbstractBaseSchemasTest {
    private static final RemoteDeviceId ID = new RemoteDeviceId("test-mount", new InetSocketAddress(99));
    private static final YangInstanceIdentifier STATE = YangInstanceIdentifier.of(NetconfState.QNAME);

    @Mock
    private Rpcs.Normalized rpc;
    @Mock
    private EffectiveModelContext modelContext;

    private void mockFuture() {
        final var successFuture = Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null));
        doReturn(successFuture, Futures.immediateFailedFuture(new IllegalStateException("Failed tx")), successFuture)
            .when(rpc).invokeNetconf(any(), any());
    }

    private static DatabindContext baseMountPointContext() {
        return BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of(
            "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring&revision=2010-10-04")))
            .databind();
    }

    @Test
    void testIgnoreNonVisibleData() {
        mockFuture();
        final var tx = new WriteCandidateTx(ID, new NetconfBaseOps(DatabindContext.ofModel(modelContext), rpc), false,
            true);
        tx.init();

        final var emptyList = ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(Filter.QNAME))
            .build();
        tx.merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(Filter.QNAME), emptyList);
        tx.put(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(Filter.QNAME), emptyList);

        verify(rpc, atMost(1)).invokeNetconf(any(), any());
    }

    @Test
    void testDiscardChanges() {
        mockFuture();
        final var tx = new WriteCandidateTx(ID, new NetconfBaseOps(DatabindContext.ofModel(modelContext), rpc), false,
            true);
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
    void testFailedCommit() {
        doReturn(
            Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null)),
            Futures.immediateFuture(new DefaultDOMRpcResult(RpcResultBuilder.newError(ErrorType.APPLICATION,
                new ErrorTag("a"), "m"))))
            .when(rpc).invokeNetconf(any(), any());

        final var tx = new WriteCandidateTx(ID, new NetconfBaseOps(DatabindContext.ofModel(modelContext), rpc), false,
            true);
        tx.init();

        final var future = tx.commit();
        assertThrows(ExecutionException.class, () -> Futures.getDone(future));
    }

    @Test
    void testDiscardChangesNotSentWithoutCandidate() {
        doReturn(
            Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null)),
            Futures.immediateFailedFuture(new IllegalStateException("Failed tx")))
            .when(rpc).invokeNetconf(any(), any());

        final var tx = new WriteRunningTx(ID, new NetconfBaseOps(BASE_SCHEMAS.baseSchemaForCapabilities(
            NetconfSessionPreferences.fromStrings(Set.of(CapabilityURN.NOTIFICATION,
                "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring"
                    + "&revision=2010-10-04"))).databind(), rpc), false, true);
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
    void testListenerSuccess() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult((ContainerNode) null)))
            .when(rpc).invokeNetconf(any(), any());
        final var tx = new WriteCandidateTx(ID, new NetconfBaseOps(baseMountPointContext(), rpc), false, true);
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
    void testListenerCancellation() {
        mockFuture();
        final var tx = new WriteCandidateTx(ID, new NetconfBaseOps(baseMountPointContext(), rpc), false, true);
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
    void testListenerFailure() {
        final var cause = new IllegalStateException("Failed tx");
        doReturn(Futures.immediateFailedFuture(cause)).when(rpc).invokeNetconf(any(), any());
        final var tx = new WriteCandidateTx(ID, new NetconfBaseOps(baseMountPointContext(), rpc), false, true);
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
