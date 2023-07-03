/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Test;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CommitRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CreateEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DeleteEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DiscardChangesRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.LockRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.MergeEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.NetconfDataTreeServiceRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.RemoveEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.ReplaceEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.UnlockRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class ProxyNetconfDataTreeServiceTest {
    private static final FiniteDuration EXP_NO_MESSAGE_TIMEOUT = Duration.apply(300, TimeUnit.MILLISECONDS);
    private static final RemoteDeviceId DEVICE_ID =
        new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.of();
    private static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;
    private static final ContainerNode NODE = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create("", "cont")))
        .build();


    private static ActorSystem system = ActorSystem.apply();

    private final TestProbe masterActor = new TestProbe(system);
    private final ProxyNetconfDataTreeService proxy = new ProxyNetconfDataTreeService(DEVICE_ID, masterActor.ref(),
        system.dispatcher(), Timeout.apply(5, TimeUnit.SECONDS));

    @AfterClass
    public static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }

    @Test
    public void testLock() {
        lock();
    }

    @Test
    public void testUnlock() {
        lock();
        proxy.unlock();
        masterActor.expectMsgClass(UnlockRequest.class);
    }

    @Test
    public void testUnlockWithoutLock() {
        try {
            proxy.unlock();
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    public void testDiscardChanges() {
        lock();
        proxy.discardChanges();
        masterActor.expectMsgClass(DiscardChangesRequest.class);
    }

    @Test
    public void testDiscardChangesWithoutLock() {
        try {
            proxy.discardChanges();
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    public void testGet() {
        proxy.get(YangInstanceIdentifier.of());
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(GetRequest.class);
    }

    @Test
    public void testGetConfig() {
        proxy.getConfig(YangInstanceIdentifier.of());
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(GetConfigRequest.class);
    }

    @Test
    public void testMerge() {
        lock();
        proxy.merge(STORE, PATH, NODE, Optional.empty());
        masterActor.expectMsgClass(MergeEditConfigRequest.class);
    }

    @Test
    public void testMergeWithoutLock() {
        try {
            proxy.merge(STORE, PATH, NODE, Optional.empty());
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    public void testReplace() {
        lock();
        proxy.replace(STORE, PATH, NODE, Optional.empty());
        masterActor.expectMsgClass(ReplaceEditConfigRequest.class);
    }

    @Test
    public void testReplaceWithoutLock() {
        try {
            proxy.replace(STORE, PATH, NODE, Optional.empty());
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    public void testCreate() {
        lock();
        proxy.create(STORE, PATH, NODE, Optional.empty());
        masterActor.expectMsgClass(CreateEditConfigRequest.class);
    }

    @Test
    public void testCreateWithoutLock() {
        try {
            proxy.create(STORE, PATH, NODE, Optional.empty());
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    public void testDelete() {
        lock();
        proxy.delete(STORE, PATH);
        masterActor.expectMsgClass(DeleteEditConfigRequest.class);
    }

    @Test
    public void testDeleteWithoutLock() {
        try {
            proxy.delete(STORE, PATH);
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    public void testRemove() {
        lock();
        proxy.remove(STORE, PATH);
        masterActor.expectMsgClass(RemoveEditConfigRequest.class);
    }

    @Test
    public void testRemoveWithoutLock() {
        try {
            proxy.remove(STORE, PATH);
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    public void testCommit() {
        lock();
        proxy.commit();
        masterActor.expectMsgClass(CommitRequest.class);
    }

    @Test
    public void testCommitWithoutLock() {
        try {
            proxy.commit();
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    private void lock() {
        final ListenableFuture<DOMRpcResult> lock = proxy.lock();
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(LockRequest.class);
        masterActor.reply(new InvokeRpcMessageReply(null, Collections.emptyList()));
        Futures.whenAllComplete(lock).run(() -> {
            assertTrue(lock.isDone());
            assertNotNull(Futures.getUnchecked(lock));
            }, MoreExecutors.directExecutor()
        );
    }

    private void checkException(final IllegalStateException exception) {
        assertEquals(String.format("%s: Device's datastore must be locked first", DEVICE_ID), exception.getMessage());
        masterActor.expectNoMessage(EXP_NO_MESSAGE_TIMEOUT);
    }
}