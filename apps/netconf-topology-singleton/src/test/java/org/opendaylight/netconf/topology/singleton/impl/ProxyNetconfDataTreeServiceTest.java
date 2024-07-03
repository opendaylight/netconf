/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
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
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

class ProxyNetconfDataTreeServiceTest {
    private static final FiniteDuration EXP_NO_MESSAGE_TIMEOUT = Duration.apply(300, TimeUnit.MILLISECONDS);
    private static final RemoteDeviceId DEVICE_ID =
        new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.of();
    private static final LogicalDatastoreType STORE = LogicalDatastoreType.CONFIGURATION;
    private static final ContainerNode NODE = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create("", "cont")))
        .build();


    private static ActorSystem system = ActorSystem.apply();

    private final TestProbe masterActor = new TestProbe(system);
    private final ProxyNetconfDataTreeService proxy = new ProxyNetconfDataTreeService(DEVICE_ID, masterActor.ref(),
        system.dispatcher(), Timeout.apply(5, TimeUnit.SECONDS));

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(system, true);
    }

    @Test
    void testLock() {
        lock();
    }

    @Test
    void testUnlock() {
        lock();
        proxy.unlock();
        masterActor.expectMsgClass(UnlockRequest.class);
    }

    @Test
    void testUnlockWithoutLock() {
        try {
            proxy.unlock();
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    void testDiscardChanges() {
        lock();
        proxy.discardChanges();
        masterActor.expectMsgClass(DiscardChangesRequest.class);
    }

    @Test
    void testDiscardChangesWithoutLock() {
        try {
            proxy.discardChanges();
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    void testGet() {
        proxy.get(YangInstanceIdentifier.of());
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(GetRequest.class);
    }

    @Test
    void testGetConfig() {
        proxy.getConfig(YangInstanceIdentifier.of());
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(GetConfigRequest.class);
    }

    @Test
    void testMerge() {
        lock();
        proxy.merge(STORE, PATH, NODE, Optional.empty());
        masterActor.expectMsgClass(MergeEditConfigRequest.class);
    }

    @Test
    void testMergeWithoutLock() {
        try {
            proxy.merge(STORE, PATH, NODE, Optional.empty());
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    void testReplace() {
        lock();
        proxy.replace(STORE, PATH, NODE, Optional.empty());
        masterActor.expectMsgClass(ReplaceEditConfigRequest.class);
    }

    @Test
    void testReplaceWithoutLock() {
        try {
            proxy.replace(STORE, PATH, NODE, Optional.empty());
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    void testCreate() {
        lock();
        proxy.create(STORE, PATH, NODE, Optional.empty());
        masterActor.expectMsgClass(CreateEditConfigRequest.class);
    }

    @Test
    void testCreateWithoutLock() {
        try {
            proxy.create(STORE, PATH, NODE, Optional.empty());
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    void testDelete() {
        lock();
        proxy.delete(STORE, PATH);
        masterActor.expectMsgClass(DeleteEditConfigRequest.class);
    }

    @Test
    void testDeleteWithoutLock() {
        try {
            proxy.delete(STORE, PATH);
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    void testRemove() {
        lock();
        proxy.remove(STORE, PATH);
        masterActor.expectMsgClass(RemoveEditConfigRequest.class);
    }

    @Test
    void testRemoveWithoutLock() {
        try {
            proxy.remove(STORE, PATH);
            fail("Should throw IllegalStateException");
        } catch (final IllegalStateException e) {
            checkException(e);
        }
    }

    @Test
    void testCommit() {
        lock();
        proxy.commit();
        masterActor.expectMsgClass(CommitRequest.class);
    }

    @Test
    void testCommitWithoutLock() {
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
        masterActor.reply(new InvokeRpcMessageReply(null, List.of()));
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