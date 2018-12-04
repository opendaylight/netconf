/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_FILTER_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import com.google.common.util.concurrent.FluentFuture;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseSchema;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class NetconfDeviceWriteOnlyTxTest {

    private final RemoteDeviceId id = new RemoteDeviceId("test-mount", new InetSocketAddress(99));

    @Mock
    private DOMRpcService rpc;
    private YangInstanceIdentifier yangIId;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        final FluentFuture<DefaultDOMRpcResult> successFuture =
                FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult((NormalizedNode<?, ?>) null));

        doReturn(successFuture)
                .doReturn(FluentFutures.immediateFailedFluentFuture(new IllegalStateException("Failed tx")))
                .doReturn(successFuture)
                .when(rpc).invokeRpc(any(SchemaPath.class), any(NormalizedNode.class));

        yangIId = YangInstanceIdentifier.builder().node(NetconfState.QNAME).build();
    }

    @Test
    public void testIgnoreNonVisibleData() {
        final WriteCandidateTx tx = new WriteCandidateTx(id, new NetconfBaseOps(rpc, mock(SchemaContext.class)),
                false);
        final MapNode emptyList = ImmutableNodes.mapNodeBuilder(NETCONF_FILTER_QNAME).build();
        tx.merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier
                .create(new YangInstanceIdentifier.NodeIdentifier(NETCONF_FILTER_QNAME)), emptyList);
        tx.put(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier
                .create(new YangInstanceIdentifier.NodeIdentifier(NETCONF_FILTER_QNAME)), emptyList);

        verify(rpc, atMost(1)).invokeRpc(any(SchemaPath.class), any(NormalizedNode.class));
    }

    @Test
    public void testDiscardChanges() throws InterruptedException {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult((NormalizedNode<?, ?>) null)))
                .when(rpc).invokeRpc(any(SchemaPath.class), isNull());

        final WriteCandidateTx tx = new WriteCandidateTx(id, new NetconfBaseOps(rpc, mock(SchemaContext.class)),
                false);
        try {
            tx.commit().get();
        } catch (final ExecutionException e) {
            // verify discard changes was sent
            final InOrder inOrder = inOrder(rpc);
            inOrder.verify(rpc).invokeRpc(toPath(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME),
                    NetconfBaseOps.getLockContent(NETCONF_CANDIDATE_QNAME));
            inOrder.verify(rpc).invokeRpc(toPath(NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME),
                    NetconfMessageTransformUtil.COMMIT_RPC_CONTENT);
            inOrder.verify(rpc).invokeRpc(eq(toPath(NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME)),
                    isNull());
            inOrder.verify(rpc).invokeRpc(toPath(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME),
                    NetconfBaseOps.getUnLockContent(NETCONF_CANDIDATE_QNAME));
            return;
        }

        fail("Submit should fail");
    }

    @Test
    public void testFailedCommit() throws Exception {
        final FluentFuture<DefaultDOMRpcResult> rpcErrorFuture = FluentFutures.immediateFluentFuture(
                new DefaultDOMRpcResult(RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "a", "m")));

        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult((NormalizedNode<?, ?>) null)))
                .doReturn(rpcErrorFuture).when(rpc).invokeRpc(any(SchemaPath.class), any(NormalizedNode.class));

        final WriteCandidateTx tx = new WriteCandidateTx(id, new NetconfBaseOps(rpc, mock(SchemaContext.class)),
                false);

        try {
            tx.commit().get();
            fail("Submit should fail");
        } catch (final ExecutionException e) {
            // Intended
        }
    }

    @Test
    public void testDiscardChangesNotSentWithoutCandidate() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult((NormalizedNode<?, ?>) null)))
                .doReturn(FluentFutures.immediateFailedFluentFuture(new IllegalStateException("Failed tx")))
                .when(rpc).invokeRpc(any(SchemaPath.class), any(NormalizedNode.class));

        final WriteRunningTx tx = new WriteRunningTx(
                id, new NetconfBaseOps(rpc, BaseSchema.BASE_NETCONF_CTX_WITH_NOTIFICATIONS.getSchemaContext()), false);

        tx.delete(LogicalDatastoreType.CONFIGURATION, yangIId);
        tx.commit();
        // verify discard changes was sent
        final InOrder inOrder = inOrder(rpc);
        inOrder.verify(rpc).invokeRpc(toPath(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME),
                NetconfBaseOps.getLockContent(NETCONF_RUNNING_QNAME));
        inOrder.verify(rpc).invokeRpc(eq(toPath(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)),
                any(NormalizedNode.class));
        inOrder.verify(rpc).invokeRpc(toPath(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME),
                NetconfBaseOps.getUnLockContent(NETCONF_RUNNING_QNAME));
    }

    @Test
    public void testListenerSuccess() throws Exception {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult((NormalizedNode<?, ?>) null)))
                .when(rpc).invokeRpc(any(SchemaPath.class), any(NormalizedNode.class));
        final WriteCandidateTx tx = new WriteCandidateTx(
                id, new NetconfBaseOps(rpc, BaseSchema.BASE_NETCONF_CTX.getSchemaContext()), false);
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
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult((NormalizedNode<?, ?>) null)))
                .when(rpc).invokeRpc(any(SchemaPath.class), isNull());
        final WriteCandidateTx tx = new WriteCandidateTx(
                id, new NetconfBaseOps(rpc, BaseSchema.BASE_NETCONF_CTX.getSchemaContext()), false);
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
        doReturn(FluentFutures.immediateFailedFluentFuture(cause))
                .when(rpc).invokeRpc(any(SchemaPath.class), any(NormalizedNode.class));
        final WriteCandidateTx tx = new WriteCandidateTx(
                id, new NetconfBaseOps(rpc, BaseSchema.BASE_NETCONF_CTX.getSchemaContext()), false);
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
