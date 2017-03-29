/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestconfOperationalStateAggregator implements StateAggregator {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfOperationalStateAggregator.class);

    @Override
    public ListenableFuture<Node> combineCreateAttempts(final List<ListenableFuture<Node>> list) {
        return combine(list);
    }

    @Override
    public ListenableFuture<Node> combineUpdateAttempts(final List<ListenableFuture<Node>> list) {
        return combine(list);
    }

    @Override
    public ListenableFuture<Void> combineDeleteAttempts(final List<ListenableFuture<Void>> list) {
        final ListenableFuture<List<Void>> allAsList = Futures.allAsList(list);
        return Futures.transform(allAsList, new Function<List<Void>, Void>() {
            @Nullable
            @Override
            public Void apply(@Nullable final List<Void> input) {
                return null;
            }
        });
    }

    private static ListenableFuture<Node> combine(final List<ListenableFuture<Node>> list) {
        final SettableFuture<Node> settableFuture = SettableFuture.create();
        final ListenableFuture<List<Node>> nodes = Futures.allAsList(list);
        Futures.addCallback(nodes, new FutureCallback<List<Node>>() {
            @Override
            public void onSuccess(@Nullable final List<Node> result) {
                Preconditions.checkNotNull(result);
                final List<ClusteredStatus> clusteredStatuses = new ArrayList<>();
                NodeId nodeId = null;
                List<Module> modules = new ArrayList<>();
                for (final Node node : result) {
                    final ClusteredNode clusteredNode = node.getAugmentation(ClusteredNode.class);
                    clusteredStatuses.addAll(clusteredNode.getClusteredConnectionStatus().getClusteredStatus());
                    final RestconfNode restconfNode = node.getAugmentation(RestconfNode.class);
                    if (nodeId == null && node.getNodeId() != null) {
                        nodeId = node.getNodeId();
                    }
                    if (restconfNode.getModule() != null && restconfNode.getModule().size() > modules.size()) {
                        modules = restconfNode.getModule();
                    }
                }
                final RestconfNode restconfNode = new RestconfNodeBuilder()
                        .setModule(modules)
                        .build();
                final ClusteredNode clusteredStatus = new ClusteredNodeBuilder()
                        .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder().setClusteredStatus(clusteredStatuses).build())
                        .build();
                final Node node = new NodeBuilder()
                        .setNodeId(nodeId)
                        .addAugmentation(RestconfNode.class, restconfNode)
                        .addAugmentation(ClusteredNode.class, clusteredStatus)
                        .build();
                settableFuture.set(node);
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("One of the combined create attempts failed", t);
                settableFuture.setException(t);
            }
        });
        return settableFuture;
    }
}
