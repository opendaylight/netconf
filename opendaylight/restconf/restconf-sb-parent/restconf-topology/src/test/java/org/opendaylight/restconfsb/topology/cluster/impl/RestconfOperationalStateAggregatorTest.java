/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

public class RestconfOperationalStateAggregatorTest {

    private static final ListenableFuture<Void> voidFuture = Futures.immediateFuture(null);
    private RestconfOperationalStateAggregator aggregator;
    private List<ListenableFuture<Node>> stateFutures;
    private RestconfNode restconfNode;

    @Before
    public void setUp() {
        aggregator = new RestconfOperationalStateAggregator();
        final Module module = new ModuleBuilder()
                .setNamespace(new Uri("namespace"))
                .setName(new YangIdentifier("name"))
                .setRevision(new RevisionIdentifier("2016-01-02"))
                .build();
        restconfNode = new RestconfNodeBuilder()
                .setModule(Collections.singletonList(module))
                .build();
        final ClusteredNode clusteredNode1 = createNode("node1", ClusteredStatus.Status.Connected);
        final Node node1 = new NodeBuilder()
                .addAugmentation(ClusteredNode.class, clusteredNode1)
                .addAugmentation(RestconfNode.class, restconfNode)
                .build();
        final ClusteredNode clusteredNode2 = createNode("node2", ClusteredStatus.Status.Connected);
        final Node node2 = new NodeBuilder()
                .addAugmentation(ClusteredNode.class, clusteredNode2)
                .addAugmentation(RestconfNode.class, restconfNode)
                .build();
        final ClusteredNode clusteredNode3 = createNode("node3", ClusteredStatus.Status.Unavailable);
        final Node node3 = new NodeBuilder()
                .addAugmentation(ClusteredNode.class, clusteredNode3)
                .addAugmentation(RestconfNode.class, restconfNode)
                .build();
        stateFutures = ImmutableList.of(Futures.immediateFuture(node1), Futures.immediateFuture(node2), Futures.immediateFuture(node3));
    }

    @Test
    public void testCombineCreateAttempts() throws ExecutionException, InterruptedException {
        final ListenableFuture<Node> combinedFuture = aggregator.combineCreateAttempts(stateFutures);
        final Map<String, ClusteredStatus.Status> statuses = new HashMap<>();
        statuses.put("node1", ClusteredStatus.Status.Connected);
        statuses.put("node2", ClusteredStatus.Status.Connected);
        statuses.put("node3", ClusteredStatus.Status.Unavailable);
        final ClusteredNode combinedNode = createCombinedNode(statuses);
        final Node expected = new NodeBuilder()
                .addAugmentation(ClusteredNode.class, combinedNode)
                .addAugmentation(RestconfNode.class, restconfNode)
                .build();
        Assert.assertTrue(combinedFuture.isDone());
        Assert.assertEquals(expected.getAugmentation(RestconfNode.class), combinedFuture.get().getAugmentation(RestconfNode.class));
        final List<ClusteredStatus> expectedClusteredStatus = expected.getAugmentation(ClusteredNode.class).getClusteredConnectionStatus().getClusteredStatus();
        final List<ClusteredStatus> actualClusteredStatus = combinedFuture.get().getAugmentation(ClusteredNode.class).getClusteredConnectionStatus().getClusteredStatus();
        Assert.assertEquals(new HashSet(expectedClusteredStatus), new HashSet(actualClusteredStatus));
    }

    @Test
    public void testSuccessfulCombineUpdateAttempts() throws ExecutionException, InterruptedException {
        final ListenableFuture<Node> combinedFuture = aggregator.combineUpdateAttempts(stateFutures);
        final Map<String, ClusteredStatus.Status> statuses = new HashMap<>();
        statuses.put("node1", ClusteredStatus.Status.Connected);
        statuses.put("node2", ClusteredStatus.Status.Connected);
        statuses.put("node3", ClusteredStatus.Status.Unavailable);
        final ClusteredNode combinedNode = createCombinedNode(statuses);
        final Node expected = new NodeBuilder()
                .addAugmentation(ClusteredNode.class, combinedNode)
                .addAugmentation(RestconfNode.class, restconfNode)
                .build();
        Assert.assertTrue(combinedFuture.isDone());
        Assert.assertEquals(expected.getAugmentation(RestconfNode.class), combinedFuture.get().getAugmentation(RestconfNode.class));
        final List<ClusteredStatus> expectedClusteredStatus = expected.getAugmentation(ClusteredNode.class).getClusteredConnectionStatus().getClusteredStatus();
        final List<ClusteredStatus> actualClusteredStatus = combinedFuture.get().getAugmentation(ClusteredNode.class).getClusteredConnectionStatus().getClusteredStatus();
        Assert.assertEquals(new HashSet(expectedClusteredStatus), new HashSet(actualClusteredStatus));
    }

    @Test
    public void testFailedCombineUpdateAttempts() throws ExecutionException, InterruptedException {
        final List<ListenableFuture<Node>> failedFutures = new ArrayList<>();
        failedFutures.addAll(stateFutures);
        final Exception cause = new Exception("error");
        failedFutures.add(Futures.<Node>immediateFailedFuture(cause));
        final ListenableFuture<Node> combinedFuture = aggregator.combineUpdateAttempts(failedFutures);
        assertTrue(combinedFuture.isDone());

        try {
            combinedFuture.get();
            fail("Exception expected");
        } catch (final Exception e) {
            assertSame(e.getCause(), cause);
        }
    }

    @Test
    public void testSuccessfulCombineDeleteAttempts() throws ExecutionException, InterruptedException {
        final List<ListenableFuture<Void>> deleteStateFutures = Lists.newArrayList(voidFuture, voidFuture);

        final ListenableFuture<Void> deleteFuture = aggregator.combineDeleteAttempts(deleteStateFutures);
        assertTrue(deleteFuture.isDone());
        assertEquals(deleteFuture.get(), null);
    }

    @Test
    public void testFailedCombineDeleteAttempts() {
        final Exception cause = new Exception("Fail");
        final List<ListenableFuture<Void>> deleteStateFutures = Lists.newArrayList(voidFuture, voidFuture,
                Futures.<Void>immediateFailedFuture(cause));

        final ListenableFuture<Void> deleteFuture = aggregator.combineDeleteAttempts(deleteStateFutures);
        assertTrue(deleteFuture.isDone());

        try {
            deleteFuture.get();
            fail("Exception expected");
        } catch (final Exception e) {
            assertSame(e.getCause(), cause);
        }
    }

    private static ClusteredNode createNode(final String node, final ClusteredStatus.Status nodeStatus) {
        final ClusteredStatus status = new ClusteredStatusBuilder().setNode(node).setStatus(nodeStatus).build();
        return new ClusteredNodeBuilder()
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder().setClusteredStatus(Collections.singletonList(status)).build())
                .build();
    }

    private static ClusteredNode createCombinedNode(final Map<String, ClusteredStatus.Status> nodeStatuses) {
        final Collection<ClusteredStatus> statuses = transformStatuses(nodeStatuses);
        return new ClusteredNodeBuilder()
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder().setClusteredStatus(ImmutableList.copyOf(statuses)).build())
                .build();
    }

    private static List<ClusteredStatus> transformStatuses(final Map<String, ClusteredStatus.Status> nodeStatuses) {
        final Collection<ClusteredStatus> statuses = Collections2.transform(nodeStatuses.entrySet(), new Function<Map.Entry<String, ClusteredStatus.Status>, ClusteredStatus>() {
            @Nullable
            @Override
            public ClusteredStatus apply(@Nullable final Map.Entry<String, ClusteredStatus.Status> input) {
                return new ClusteredStatusBuilder().setNode(input.getKey()).setStatus(input.getValue()).build();
            }
        });
        return ImmutableList.copyOf(statuses);
    }

}