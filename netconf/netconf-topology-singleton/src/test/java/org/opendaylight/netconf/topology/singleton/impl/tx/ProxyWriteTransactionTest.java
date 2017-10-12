/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

public class ProxyWriteTransactionTest {
    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.EMPTY;
    private static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;

    private ActorSystem system;
    private TestProbe masterActor;
    private ContainerNode node;
    private ProxyWriteTransaction tx;

    @Before
    public void setUp() throws Exception {
        system = ActorSystem.apply();
        masterActor = new TestProbe(system);
        final RemoteDeviceId id = new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
        node = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("", "cont")))
                .build();
        tx = new ProxyWriteTransaction(masterActor.ref(), id, system, Timeout.apply(5, TimeUnit.SECONDS));
    }

    @After
    public void tearDown() throws Exception {
        JavaTestKit.shutdownActorSystem(system, null, true);
    }

    @Test
    public void testCancel() throws Exception {
        final Future<Boolean> submit = Executors.newSingleThreadExecutor().submit(() -> tx.cancel());
        masterActor.expectMsgClass(CancelRequest.class);
        masterActor.reply(true);
        Assert.assertTrue(submit.get());
    }

    @Test
    public void testCancelSubmitted() throws Exception {
        final ListenableFuture<Void> submitFuture = tx.submit();
        masterActor.expectMsgClass(SubmitRequest.class);
        masterActor.reply(new SubmitReply());
        submitFuture.get();
        final Future<Boolean> submit = Executors.newSingleThreadExecutor().submit(() -> tx.cancel());
        masterActor.expectNoMsg();
        Assert.assertFalse(submit.get());
    }

    @Test
    public void testSubmit() throws Exception {
        final ListenableFuture<Void> submitFuture = tx.submit();
        masterActor.expectMsgClass(SubmitRequest.class);
        masterActor.reply(new SubmitReply());
        submitFuture.get();
    }

    @Test
    public void testDoubleSubmit() throws Exception {
        final ListenableFuture<Void> submitFuture = tx.submit();
        masterActor.expectMsgClass(SubmitRequest.class);
        masterActor.reply(new SubmitReply());
        submitFuture.get();
        try {
            tx.submit().checkedGet();
            Assert.fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            masterActor.expectNoMsg();
        }
    }

    @Test
    public void testCommit() throws Exception {
        final ListenableFuture<RpcResult<TransactionStatus>> submitFuture = tx.commit();
        masterActor.expectMsgClass(SubmitRequest.class);
        masterActor.reply(new SubmitReply());
        Assert.assertEquals(TransactionStatus.SUBMITED, submitFuture.get().getResult());
    }

    @Test
    public void testDelete() throws Exception {
        tx.delete(STORE, PATH);
        masterActor.expectMsgClass(DeleteRequest.class);
    }

    @Test
    public void testDeleteClosed() throws Exception {
        submit();
        try {
            tx.delete(STORE, PATH);
            Assert.fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            masterActor.expectNoMsg();
        }
    }

    @Test
    public void testPut() throws Exception {
        tx.put(STORE, PATH, node);
        masterActor.expectMsgClass(PutRequest.class);
    }

    @Test
    public void testPutClosed() throws Exception {
        submit();
        try {
            tx.put(STORE, PATH, node);
            Assert.fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            masterActor.expectNoMsg();
        }
    }

    @Test
    public void testMerge() throws Exception {
        tx.merge(STORE, PATH, node);
        masterActor.expectMsgClass(MergeRequest.class);
    }

    @Test
    public void testMergeClosed() throws Exception {
        submit();
        try {
            tx.merge(STORE, PATH, node);
            Assert.fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            masterActor.expectNoMsg();
        }
    }

    @Test
    public void testGetIdentifier() throws Exception {
        Assert.assertEquals(tx, tx.getIdentifier());
    }

    private void submit() throws TransactionCommitFailedException {
        final CheckedFuture<Void, TransactionCommitFailedException> submit = tx.submit();
        masterActor.expectMsgClass(SubmitRequest.class);
        masterActor.reply(new SubmitReply());
        submit.checkedGet();
    }

}