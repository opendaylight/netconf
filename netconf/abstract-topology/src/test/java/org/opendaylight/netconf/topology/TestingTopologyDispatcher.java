/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.topology.impl.ConnectionStatusListenerRegistration;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestingTopologyDispatcher implements NetconfTopology {

    private static final Logger LOG = LoggerFactory.getLogger(TestingTopologyDispatcher.class);

    private final String topologyId;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Set<NodeId> connected = new HashSet<>();
    private final Map<NodeId, RemoteDeviceHandler<NetconfSessionPreferences>> listeners = new HashMap<>();


    public TestingTopologyDispatcher(final String topologyId) {

        this.topologyId = topologyId;
    }

    @Override
    public String getTopologyId() {
        return topologyId;
    }

    @Override
    public DataBroker getDataBroker() {
        return null;
    }

    // log the current connection attempt and return a successful future asynchronously
    @Override
    public ListenableFuture<NetconfDeviceCapabilities> connectNode(final NodeId nodeId, final Node configNode) {
        final NetconfNode augmentation = configNode.getAugmentation(NetconfNode.class);
        LOG.debug("Connecting node {}, with config: {} ", nodeId.getValue(),
                augmentation.getHost().getIpAddress().toString() + ":" + augmentation.getPort());
        connected.add(nodeId);
        final SettableFuture<NetconfDeviceCapabilities> future = SettableFuture.create();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(4000);
                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            future.set(new NetconfDeviceCapabilities());
                        }
                    });
                } catch (InterruptedException e) {
                    LOG.error("Cannot sleep thread", e);
                }
            }
        });
        return future;
    }

    @Override
    public ListenableFuture<Void> disconnectNode(final NodeId nodeId) {
        Preconditions.checkState(connected.contains(nodeId), "Node is not connected yet");
        LOG.debug("Disconnecting node {}", nodeId.getValue());
        final SettableFuture<Void> future = SettableFuture.create();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(4000);
                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            connected.remove(nodeId);
                            future.set(null);
                        }
                    });
                } catch (InterruptedException e) {
                    LOG.error("Cannot sleep thread", e);
                }

            }
        });
        return future;
    }

    @Override
    public void registerMountPoint(ActorContext context, NodeId nodeId) {
        LOG.debug("Registering mount point for node {}", nodeId.getValue());
    }

    @Override
    public void registerMountPoint(ActorContext context, NodeId nodeId, ActorRef masterRef) {
        LOG.debug("Registering mount point for node {}", nodeId.getValue());
    }

    @Override
    public void unregisterMountPoint(NodeId nodeId) {
        LOG.debug("Unregistering mount point for node {}", nodeId.getValue());
    }

    @Override
    public ConnectionStatusListenerRegistration registerConnectionStatusListener(final NodeId node, final RemoteDeviceHandler<NetconfSessionPreferences> listener) {
        Preconditions.checkState(connected.contains(node), "Node is not connected yet");

        LOG.debug("Registering a connection status listener for node {}", node.getValue());
        listeners.put(node, listener);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10000);

                    boolean up = false;
                    for (int i = 0; i < 20; i++) {
                        if (up) {
                            LOG.debug("Device has connected {}", node.getValue());
                            listener.onDeviceConnected(null, null, null);
                            up = false;
                        } else {
                            LOG.debug("Device has diconnected {}", node.getValue());
                            listener.onDeviceDisconnected();
                            up = true;
                        }
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            LOG.error("Cannot sleep thread", e);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        });

        return null;
    }
}
