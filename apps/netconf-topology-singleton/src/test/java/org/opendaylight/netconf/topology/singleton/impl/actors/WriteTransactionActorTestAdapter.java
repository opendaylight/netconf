/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.NODE;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.PATH;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.STORE;
import static org.opendaylight.netconf.topology.singleton.impl.actors.ReadTransactionActorTestAdapter.TIMEOUT;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status.Failure;
import org.apache.pekko.actor.Status.Success;
import org.apache.pekko.testkit.TestProbe;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * Adapter for write transaction tests.
 *
 * @author Thomas Pantelis
 */
abstract class WriteTransactionActorTestAdapter {
    private DOMDataTreeWriteTransaction mockWriteTx;
    private TestProbe probe;
    private ActorRef actorRef;
    private ActorSystem system;

    void init(final DOMDataTreeWriteTransaction inMockWriteTx, final ActorSystem inSystem,
            final ActorRef inActorRef) {
        this.mockWriteTx = inMockWriteTx;
        this.probe = TestProbe.apply(inSystem);
        this.actorRef = inActorRef;
        this.system = inSystem;
    }

    @Test
    void testPut() {
        final NormalizedNodeMessage normalizedNodeMessage = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new PutRequest(STORE, normalizedNodeMessage), probe.ref());
        verify(mockWriteTx).put(STORE, PATH, NODE);
    }

    @Test
    void testMerge() {
        final NormalizedNodeMessage normalizedNodeMessage = new NormalizedNodeMessage(PATH, NODE);
        actorRef.tell(new MergeRequest(STORE, normalizedNodeMessage), probe.ref());
        verify(mockWriteTx).merge(STORE, PATH, NODE);
    }

    @Test
    void testDelete() {
        actorRef.tell(new DeleteRequest(STORE, PATH), probe.ref());
        verify(mockWriteTx).delete(STORE, PATH);
    }

    @Test
    void testCancel() {
        when(mockWriteTx.cancel()).thenReturn(true);
        actorRef.tell(new CancelRequest(), probe.ref());

        verify(mockWriteTx).cancel();
        probe.expectMsg(true);
    }

    @Test
    void testSubmit() {
        doReturn(emptyFluentFuture()).when(mockWriteTx).commit();
        actorRef.tell(new SubmitRequest(), probe.ref());

        verify(mockWriteTx).commit();
        probe.expectMsgClass(Success.class);
    }

    @Test
    void testSubmitFail() {
        final RpcError rpcError =
                RpcResultBuilder.newError(ErrorType.APPLICATION, new ErrorTag("fail"), "fail");
        final TransactionCommitFailedException cause = new TransactionCommitFailedException("fail", rpcError);
        when(mockWriteTx.commit()).thenReturn(FluentFutures.immediateFailedFluentFuture(cause));
        actorRef.tell(new SubmitRequest(), probe.ref());

        verify(mockWriteTx).commit();
        final Failure response = probe.expectMsgClass(Failure.class);
        assertEquals(cause, response.cause());
    }

    @Test
    void testIdleTimeout() {
        final TestProbe testProbe = new TestProbe(system);
        testProbe.watch(actorRef);
        verify(mockWriteTx, timeout(3000)).cancel();
        testProbe.expectTerminated(actorRef, TIMEOUT.duration());
    }
}
