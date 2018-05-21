/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.NODE;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.PATH;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.STORE;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.TIMEOUT;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status.Failure;
import akka.actor.Status.Success;
import akka.testkit.TestProbe;
import com.google.common.util.concurrent.Futures;
import org.junit.Test;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * Adapter for write transaction tests.
 *
 * @author Thomas Pantelis
 */
public abstract class WriteTransactionActorTestAdapter {
    private DOMDataWriteTransaction mockWriteTx;
    private TestProbe probe;
    private ActorRef actorRef;
    private ActorSystem system;

    public void init(DOMDataWriteTransaction inMockWriteTx, ActorSystem inSystem, ActorRef inActorRef) {
        this.mockWriteTx = inMockWriteTx;
        this.probe = TestProbe.apply(inSystem);
        this.actorRef = inActorRef;
        this.system = inSystem;
    }

    @Test
    public void testPut() {
        final NormalizedNodeMessage normalizedNodeMessage = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new PutRequest(STORE, normalizedNodeMessage), probe.ref());
        verify(mockWriteTx).put(STORE, PATH, NODE);
    }

    @Test
    public void testMerge() {
        final NormalizedNodeMessage normalizedNodeMessage = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new MergeRequest(STORE, normalizedNodeMessage), probe.ref());
        verify(mockWriteTx).merge(STORE, PATH, NODE);
    }

    @Test
    public void testDelete() {
        actorRef.tell(new DeleteRequest(STORE, PATH), probe.ref());
        verify(mockWriteTx).delete(STORE, PATH);
    }

    @Test
    public void testCancel() throws Exception {
        when(mockWriteTx.cancel()).thenReturn(true);
        actorRef.tell(new CancelRequest(), probe.ref());

        verify(mockWriteTx).cancel();
        probe.expectMsg(true);
    }

    @Test
    public void testSubmit() throws Exception {
        when(mockWriteTx.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        actorRef.tell(new SubmitRequest(), probe.ref());

        verify(mockWriteTx).submit();
        probe.expectMsgClass(Success.class);
    }

    @Test
    public void testSubmitFail() throws Exception {
        final RpcError rpcError =
                RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "fail", "fail");
        final TransactionCommitFailedException cause = new TransactionCommitFailedException("fail", rpcError);
        when(mockWriteTx.submit()).thenReturn(Futures.immediateFailedCheckedFuture(cause));
        actorRef.tell(new SubmitRequest(), probe.ref());

        verify(mockWriteTx).submit();
        final Failure response = probe.expectMsgClass(Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    public void testIdleTimeout() throws Exception {
        final TestProbe testProbe = new TestProbe(system);
        testProbe.watch(actorRef);
        verify(mockWriteTx, timeout(3000)).cancel();
        testProbe.expectTerminated(actorRef, TIMEOUT.duration());
    }
}
