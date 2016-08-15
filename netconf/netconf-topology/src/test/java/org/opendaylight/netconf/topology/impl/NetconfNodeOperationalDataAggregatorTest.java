/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

public class NetconfNodeOperationalDataAggregatorTest {

    private List<ListenableFuture<Node>> stateFutures;

    private NetconfNodeOperationalDataAggregator aggregator;

    @Before
    public void setUp() {
        aggregator = new NetconfNodeOperationalDataAggregator();
        stateFutures = Lists.newArrayList();
    }

    @Test
    public void testCombineCreateAttempts() throws ExecutionException, InterruptedException {
        NetconfNode testingNode = new NetconfNodeBuilder().setAvailableCapabilities(
                new AvailableCapabilitiesBuilder().setAvailableCapability(Lists.<AvailableCapability>newArrayList()).build())
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder().setNodeStatus(Lists.newArrayList(
                        new NodeStatusBuilder().setStatus(NodeStatus.Status.Connected).build())).build())
                .setConnectionStatus(NetconfNodeConnectionStatus.ConnectionStatus.Connected).build();
        stateFutures.add(Futures.immediateFuture(new NodeBuilder().addAugmentation(NetconfNode.class, testingNode).build()));

        ListenableFuture<Node> aggregatedCreateFuture = aggregator.combineCreateAttempts(stateFutures);
        assertTrue(aggregatedCreateFuture.isDone());

        NetconfNode aggregatedNode = aggregatedCreateFuture.get().getAugmentation(NetconfNode.class);
        assertEquals(aggregatedNode.getClusteredConnectionStatus().getNodeStatus().get(0).getStatus(),
                NodeStatus.Status.Connected);
    }

    @Test
    public void testSuccessfulCombineUpdateAttempts() throws ExecutionException, InterruptedException {
        NetconfNode testingNode = new NetconfNodeBuilder().setAvailableCapabilities(
                new AvailableCapabilitiesBuilder().setAvailableCapability(Lists.<AvailableCapability>newArrayList()).build())
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder().setNodeStatus(Lists.newArrayList(
                        new NodeStatusBuilder().setStatus(NodeStatus.Status.Connected).build())).build())
                .setConnectionStatus(NetconfNodeConnectionStatus.ConnectionStatus.Connected).build();
        stateFutures.add(Futures.immediateFuture(new NodeBuilder().addAugmentation(NetconfNode.class, testingNode).build()));

        ListenableFuture<Node> aggregatedUpdateFuture = aggregator.combineUpdateAttempts(stateFutures);
        assertTrue(aggregatedUpdateFuture.isDone());

        NetconfNode aggregatedNode = aggregatedUpdateFuture.get().getAugmentation(NetconfNode.class);
        assertEquals(aggregatedNode.getClusteredConnectionStatus().getNodeStatus().get(0).getStatus(),
                NodeStatus.Status.Connected);
    }

    @Test
    public void testSuccessfulCombineDeleteAttempts() throws ExecutionException, InterruptedException {
        List deleteStateFutures = Lists.newArrayList(Futures.immediateFuture(null), Futures.immediateFuture(null));

        ListenableFuture<Void> deleteFuture = aggregator.combineDeleteAttempts(deleteStateFutures);
        assertTrue(deleteFuture.isDone());
        assertEquals(deleteFuture.get(), null);
    }

    @Test
    public void testFailedCombineDeleteAttempts() {
        Exception cause = new Exception("Fail");
        List deleteStateFutures = Lists.newArrayList(Futures.immediateFuture(null), Futures.immediateFuture(null),
                Futures.immediateFailedFuture(cause));

        ListenableFuture<Void> deleteFuture = aggregator.combineDeleteAttempts(deleteStateFutures);
        assertTrue(deleteFuture.isDone());

        try {
            deleteFuture.get();
            fail("Exception expected");
        } catch(Exception e) {
            assertSame(e.getCause(), cause);
        }
    }
}
