/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.netconf.topology.UserDefinedMessage;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeFields;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

public class ExampleNodeManagerCallback implements NodeManagerCallback<UserDefinedMessage> {

    private Peer.PeerContext<UserDefinedMessage> peerCtx;
    private NetconfTopology topologyDispatcher;

    public ExampleNodeManagerCallback(NetconfTopology topologyDispatcher) {
        this.topologyDispatcher = topologyDispatcher;
    }


    @Nonnull
    @Override public Node getInitialState(@Nonnull final NodeId nodeId,
                                          @Nonnull final Node configNode) {
        return new NodeBuilder().addAugmentation(NetconfNode.class,
                new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connecting).build()).build();
    }

    @Nonnull @Override public Node getFailedState(@Nonnull final NodeId nodeId,
                                                  @Nonnull final Node configNode) {
        return new NodeBuilder().addAugmentation(NetconfNode.class,
                new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.UnableToConnect).build()).build();
    }

    @Nonnull @Override public ListenableFuture<Node> nodeCreated(@Nonnull final NodeId nodeId,
                                                                 @Nonnull final Node configNode) {
        // connect magic
        final ListenableFuture<NetconfDeviceCapabilities> connectionFuture = topologyDispatcher.connectNode(nodeId, configNode);

        final NetconfNode netconfNode = configNode.getAugmentation(NetconfNode.class);

        // transform future result into state that gets written into datastore
        return Futures.transform(connectionFuture, new Function<NetconfDeviceCapabilities, Node>() {
            @Nullable
            @Override
            public Node apply(NetconfDeviceCapabilities input) {
                // build state data
                return new NodeBuilder().addAugmentation(NetconfNode.class,
                        new NetconfNodeBuilder()
                                .setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connected)
                                .setHost(netconfNode.getHost())
                                .setPort(netconfNode.getPort())
                                .build()).build();
            }
        });
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> nodeUpdated(@Nonnull final NodeId nodeId,
                                              @Nonnull final Node configNode) {
        // first disconnect this node
        topologyDispatcher.disconnectNode(nodeId);

        // now reinit this connection with new settings
        // TODO add a listener into the device communicator that will notify for connection changes
        final ListenableFuture<NetconfDeviceCapabilities> connectionFuture = topologyDispatcher.connectNode(nodeId, configNode);

        return Futures.transform(connectionFuture, new Function<NetconfDeviceCapabilities, Node>() {
            @Nullable
            @Override
            public Node apply(NetconfDeviceCapabilities input) {
                // build state data
                return new NodeBuilder().addAugmentation(NetconfNode.class,
                        new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connected).build()).build();
            }
        });
    }

    @Nonnull @Override public ListenableFuture<Void> nodeDeleted(@Nonnull final NodeId nodeId) {
        // Disconnect
        return topologyDispatcher.disconnectNode(nodeId);
    }

    @Override public void setPeerContext(final Peer.PeerContext<UserDefinedMessage> peerContext) {
        peerCtx = peerContext;
        peerContext.notifyPeers(new UserDefinedMessage() {});
    }

    @Override public void handle(final UserDefinedMessage msg) {
        // notifications from peers
    }

}