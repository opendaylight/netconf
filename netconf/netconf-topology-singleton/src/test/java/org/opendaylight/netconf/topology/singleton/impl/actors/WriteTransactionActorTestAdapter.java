/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.NODE;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.PATH;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.STORE;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.TIMEOUT;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status.Failure;
import akka.actor.Status.Success;
import akka.testkit.TestProbe;
import org.junit.Test;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * Adapter for write transaction tests.
 *
 * @author Thomas Pantelis
 */
public abstract class WriteTransactionActorTestAdapter {
    private DOMDataTreeWriteTransaction mockWriteTx;
    private TestProbe probe;
    private ActorRef actorRef;
    private ActorSystem system;

    public void init(final DOMDataTreeWriteTransaction inMockWriteTx, final ActorSystem inSystem,
            final ActorRef inActorRef) {
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
    public void testCancel() {
        when(mockWriteTx.cancel()).thenReturn(true);
        actorRef.tell(new CancelRequest(), probe.ref());

        verify(mockWriteTx).cancel();
        probe.expectMsg(true);
    }

    @Test
    public void testSubmit() {
        doReturn(emptyFluentFuture()).when(mockWriteTx).commit();
        actorRef.tell(new SubmitRequest(), probe.ref());

        verify(mockWriteTx).commit();
        probe.expectMsgClass(Success.class);
    }

    @Test
    public void testSubmitFail() {
        final RpcError rpcError =
                RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "fail", "fail");
        final TransactionCommitFailedException cause = new TransactionCommitFailedException("fail", rpcError);
        when(mockWriteTx.commit()).thenReturn(FluentFutures.immediateFailedFluentFuture(cause));
        actorRef.tell(new SubmitRequest(), probe.ref());

        verify(mockWriteTx).commit();
        final Failure response = probe.expectMsgClass(Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    public void testIdleTimeout() {
        final TestProbe testProbe = new TestProbe(system);
        testProbe.watch(actorRef);
        verify(mockWriteTx, timeout(3000)).cancel();
        testProbe.expectTerminated(actorRef, TIMEOUT.duration());
    }
}
