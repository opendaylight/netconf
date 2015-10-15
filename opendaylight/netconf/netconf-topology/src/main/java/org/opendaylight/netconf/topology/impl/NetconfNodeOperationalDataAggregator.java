/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfNodeOperationalDataAggregator implements StateAggregator{

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeOperationalDataAggregator.class);

    @Override
    public ListenableFuture<Node> combineCreateAttempts(final List<ListenableFuture<Node>> stateFutures) {
        final SettableFuture<Node> future = SettableFuture.create();
        final ListenableFuture<List<Node>> allAsList = Futures.allAsList(stateFutures);
        Futures.addCallback(allAsList, new FutureCallback<List<Node>>() {
            @Override
            public void onSuccess(final List<Node> result) {
                Node base = null;
                NetconfNode baseAugmentation = null;
                final ArrayList<NodeStatus> statusList = new ArrayList<>();
                for (final Node node : result) {
                    final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
                    if (base == null && netconfNode.getConnectionStatus().equals(ConnectionStatus.Connected)) {
                        base = node;
                        baseAugmentation = netconfNode;
                    }
                    LOG.debug(netconfNode.toString());
                    statusList.addAll(netconfNode.getClusteredConnectionStatus().getNodeStatus());
                }

                if (base == null) {
                    base = result.get(0);
                    baseAugmentation = result.get(0).getAugmentation(NetconfNode.class);
                    LOG.warn("All results {}", result.toString());
                }

                final Node aggregatedNode =
                        new NodeBuilder(base)
                                .addAugmentation(NetconfNode.class,
                                        new NetconfNodeBuilder(baseAugmentation)
                                                .setClusteredConnectionStatus(
                                                        new ClusteredConnectionStatusBuilder()
                                                                .setNodeStatus(statusList)
                                                                .build())
                                                .build())
                                .build();
                future.set(aggregatedNode);
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("One of the combined create attempts failed {}", t);
                future.setException(t);
            }
        });
        return future;
    }

    @Override
    public ListenableFuture<Node> combineUpdateAttempts(final List<ListenableFuture<Node>> stateFutures) {
        final SettableFuture<Node> future = SettableFuture.create();
        final ListenableFuture<List<Node>> allAsList = Futures.allAsList(stateFutures);
        Futures.addCallback(allAsList, new FutureCallback<List<Node>>() {
            @Override
            public void onSuccess(final List<Node> result) {
                Node base = null;
                NetconfNode baseAugmentation = null;
                final ArrayList<NodeStatus> statusList = new ArrayList<>();
                for (final Node node : result) {
                    final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
                    if (base == null && netconfNode.getConnectionStatus().equals(ConnectionStatus.Connected)) {
                        base = node;
                        baseAugmentation = netconfNode;
                    }
                    LOG.debug(netconfNode.toString());
                    statusList.addAll(netconfNode.getClusteredConnectionStatus().getNodeStatus());
                }

                if (base == null) {
                    base = result.get(0);
                    baseAugmentation = result.get(0).getAugmentation(NetconfNode.class);
                    LOG.warn("All results {}", result.toString());
                }

                final Node aggregatedNode =
                        new NodeBuilder(base)
                                .addAugmentation(NetconfNode.class,
                                        new NetconfNodeBuilder(baseAugmentation)
                                                .setClusteredConnectionStatus(
                                                        new ClusteredConnectionStatusBuilder()
                                                                .setNodeStatus(statusList)
                                                                .build())
                                                .build())
                                .build();
                future.set(aggregatedNode);
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("One of the combined update attempts failed {}", t);
                future.setException(t);
            }
        });
        return future;
    }

    @Override
    public ListenableFuture<Void> combineDeleteAttempts(final List<ListenableFuture<Void>> stateFutures) {
        final SettableFuture<Void> future = SettableFuture.create();
        final ListenableFuture<List<Void>> allAsList = Futures.allAsList(stateFutures);
        Futures.addCallback(allAsList, new FutureCallback<List<Void>>() {
            @Override
            public void onSuccess(final List<Void> result) {
                future.set(null);
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("One of the combined delete attempts failed {}", t);
                future.setException(t);
            }
        });
        return future;
    }

    private boolean operationalDataEquals(final Node node1, final Node node2) {
        if (!node1.getNodeId().equals(node2.getNodeId())) {
            return false;
        }
        final NetconfNode netconfNode1 = node1.getAugmentation(NetconfNode.class);
        final NetconfNode netconfNode2 = node2.getAugmentation(NetconfNode.class);

        if (!netconfNode1.getConnectionStatus().equals(netconfNode2.getConnectionStatus())) {
            return false;
        }

        if (netconfNode1.getAvailableCapabilities() == null) {
            if (netconfNode2.getAvailableCapabilities() != null) {
                return false;
            }
        } else {
            if (!netconfNode1.getAvailableCapabilities().equals(netconfNode2.getAvailableCapabilities())) {
                return false;
            }
        }

        if (netconfNode1.getUnavailableCapabilities() == null) {
            if (netconfNode2.getUnavailableCapabilities() != null) {
                return false;
            }
        } else {
            if (!netconfNode1.getUnavailableCapabilities().equals(netconfNode2.getUnavailableCapabilities())) {
                return false;
            }
        }

        return true;
    }
}
