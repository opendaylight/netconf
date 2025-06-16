/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CommitRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CreateEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DeleteEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.MergeEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.NetconfDataTreeServiceRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.RemoveEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.ReplaceEditConfigRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class ProxyNetconfDataTreeServiceTest {
    private static final RemoteDeviceId DEVICE_ID =
        new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.of();
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
    void testGet() {
        proxy.get(LogicalDatastoreType.OPERATIONAL, PATH, List.of());
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(GetRequest.class);
    }

    @Test
    void testGetConfig() {
        proxy.get(LogicalDatastoreType.CONFIGURATION, PATH, List.of());
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(GetRequest.class);
    }

    @Test
    void testMerge() {
        proxy.merge(PATH, NODE);
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(MergeEditConfigRequest.class);
    }

    @Test
    void testReplace() {
        proxy.replace(PATH, NODE);
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(ReplaceEditConfigRequest.class);
    }

    @Test
    void testCreate() {
        proxy.create(PATH, NODE);
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(CreateEditConfigRequest.class);
    }

    @Test
    void testDelete() {
        proxy.delete(PATH);
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(DeleteEditConfigRequest.class);
    }

    @Test
    void testRemove() {
        proxy.remove(PATH);
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(RemoveEditConfigRequest.class);
    }

    @Test
    void testCommit() {
        proxy.commit();
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
        masterActor.reply(new Status.Success(masterActor.ref()));
        masterActor.expectMsgClass(CommitRequest.class);
    }
}