/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class WriteTransactionActorTest {
    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.EMPTY;
    private static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;
    private static final Timeout TIMEOUT = Timeout.apply(5, TimeUnit.SECONDS);

    @Mock
    private DOMDataWriteTransaction deviceWriteTx;
    private TestProbe probe;
    private ActorSystem system;
    private TestActorRef<WriteTransactionActor> actorRef;
    private NormalizedNode<?, ?> node;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        system = ActorSystem.apply();
        probe = TestProbe.apply(system);
        node = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("cont")))
                .build();
        actorRef = TestActorRef.create(system, WriteTransactionActor.props(deviceWriteTx,
                Duration.apply(2, TimeUnit.SECONDS)), "testA");
    }

    @After
    public void tearDown() throws Exception {
        JavaTestKit.shutdownActorSystem(system, null, true);
    }

    @Test
    public void testPut() throws Exception {
        final NormalizedNodeMessage normalizedNodeMessage = new NormalizedNodeMessage(PATH, node);
        actorRef.tell(new PutRequest(STORE, normalizedNodeMessage), probe.ref());
        verify(deviceWriteTx).put(STORE, PATH, node);
    }

    @Test
    public void testMerge() throws Exception {
        final NormalizedNodeMessage normalizedNodeMessage = new NormalizedNodeMessage(PATH, node);
        actorRef.tell(new MergeRequest(STORE, normalizedNodeMessage), probe.ref());
        verify(deviceWriteTx).merge(STORE, PATH, node);
    }

    @Test
    public void testDelete() throws Exception {
        actorRef.tell(new DeleteRequest(STORE, PATH), probe.ref());
        verify(deviceWriteTx).delete(STORE, PATH);
    }

    @Test
    public void testCancel() throws Exception {
        when(deviceWriteTx.cancel()).thenReturn(true);
        final Future<Object> cancelFuture = Patterns.ask(actorRef, new CancelRequest(), TIMEOUT);
        final Object result = Await.result(cancelFuture, TIMEOUT.duration());
        Assert.assertTrue(result instanceof Boolean);
        verify(deviceWriteTx).cancel();
        Assert.assertTrue((Boolean) result);
    }

    @Test
    public void testSubmit() throws Exception {
        when(deviceWriteTx.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        final Future<Object> submitFuture = Patterns.ask(actorRef, new SubmitRequest(), TIMEOUT);
        final Object result = Await.result(submitFuture, TIMEOUT.duration());
        Assert.assertTrue(result instanceof SubmitReply);
        verify(deviceWriteTx).submit();
    }

    @Test
    public void testSubmitFail() throws Exception {
        final RpcError rpcError =
                RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "fail", "fail");
        final TransactionCommitFailedException cause = new TransactionCommitFailedException("fail", rpcError);
        when(deviceWriteTx.submit()).thenReturn(Futures.immediateFailedCheckedFuture(cause));
        final Future<Object> submitFuture = Patterns.ask(actorRef, new SubmitRequest(), TIMEOUT);
        final Object result = Await.result(submitFuture, TIMEOUT.duration());
        Assert.assertEquals(cause, result);
        verify(deviceWriteTx).submit();
    }

    @Test
    public void testIdleTimeout() throws Exception {
        final TestProbe probe = new TestProbe(system);
        probe.watch(actorRef);
        verify(deviceWriteTx, timeout(3000)).cancel();
        probe.expectTerminated(actorRef, TIMEOUT.duration());
    }

}