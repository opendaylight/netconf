/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx.proxy;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.TransactionInUseException;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.OpenTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import scala.concurrent.Await;
import scala.concurrent.duration.FiniteDuration;

public class ProxyDOMWriteTransactionTest {

    private ProxyDOMWriteTransaction tx;
    private ExecutorService executor;
    private ActorRef requester;
    private TestProbe probe;
    private ActorSystem system;

    @Before
    public void setUp() throws Exception {
        final RemoteDeviceId id = new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
        system = ActorSystem.apply();
        probe = new TestProbe(system);
        tx = new ProxyDOMWriteTransaction(id, system, probe.testActor());
        executor = Executors.newSingleThreadExecutor();
        //proxy doesn't check requester, can be any actor ref
        requester = probe.testActor();
    }

    @Test(expected = TransactionInUseException.class)
    public void testMultipleOpenNoClose() throws Exception {
        openTx();
        tx.openTransaction(requester);
    }

    @Test()
    public void testSubmitOpened() throws Exception {
        openTx();
        final scala.concurrent.Future<Void> submit = tx.submit(requester);
        probe.expectMsgClass(SubmitRequest.class);
        probe.reply(new SubmitReply());
        Await.result(submit, FiniteDuration.apply(5, TimeUnit.SECONDS));
    }

    @Test
    public void testSubmitNotOpened() throws Exception {
        try {
            tx.submit(requester);
            Assert.fail("Submit without open should throw exception");
        } catch (final IllegalStateException e) {
            //submit on not opened tx shouldn't send any message
            probe.expectNoMsg();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testDoubleSubmit() throws Exception {
        openTx();
        tx.submit(requester);
        tx.submit(requester);
    }

    @Test
    public void testCancelOpened() throws Exception {
        openTx();
        final Future<Boolean> cancel = call(() -> tx.cancel(requester));
        //cancel should send message to master
        probe.expectMsgClass(CancelRequest.class);
        probe.reply(Boolean.TRUE);
        Assert.assertTrue(cancel.get());
        //second cancel should return false and shouldn't send any message
        Assert.assertFalse(tx.cancel(requester));
        probe.expectNoMsg();
    }

    @Test
    public void testCancelNotOpened() throws Exception {
        Assert.assertFalse(tx.cancel(requester));
        //cancel on not opened tx should return false and shouldn't send any message
        probe.expectNoMsg();
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdownNow();
        JavaTestKit.shutdownActorSystem(system);
    }

    private void openTx() throws InterruptedException, java.util.concurrent.ExecutionException {
        final Future<?> open1 = run(() -> {
            try {
                tx.openTransaction(requester);
            } catch (final TransactionInUseException e) {
                throw new IllegalStateException(e);
            }
        });
        probe.expectMsgClass(OpenTransaction.class);
        probe.reply(new Object());
        open1.get();
    }

    private <T> Future<T> call(final Callable<T> task) {
        return executor.submit(task);
    }

    private Future<?> run(final Runnable task) {
        return executor.submit(task);
    }
}