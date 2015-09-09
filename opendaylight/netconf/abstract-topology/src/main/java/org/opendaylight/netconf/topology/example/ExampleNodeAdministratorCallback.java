/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.NodeAdministratorCallback;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.netconf.topology.example.ExampleTopology.CustomMessage;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeFields;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

public class ExampleNodeAdministratorCallback implements NodeAdministratorCallback<CustomMessage> {

    private Peer.PeerContext<CustomMessage> peerCtx;
    private NetconfTopology topologyDispatcher;

    public ExampleNodeAdministratorCallback(NetconfTopology topologyDispatcher) {
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

    @Nonnull @Override public ListenableFuture<Node> connect(@Nonnull final NodeId nodeId,
                                                             @Nonnull final Node configNode) {
        // connect magic
        final ListenableFuture<Void> connectionFuture = topologyDispatcher.connectNode(nodeId, configNode);
        // add listener, return succes/failure

        return Futures.immediateFuture(new NodeBuilder().addAugmentation(NetconfNode.class,
                new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connected).build()).build());
    }

    @Nonnull @Override public ListenableFuture<Node> update(@Nonnull final NodeId nodeId,
                                                            @Nonnull final Node configNode) {
        // update magic
        return Futures.immediateFuture(new NodeBuilder().addAugmentation(NetconfNode.class,
                new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connected).build()).build());
    }

    @Nonnull @Override public ListenableFuture<Void> delete(@Nonnull final NodeId nodeId) {
        // Disconnect
        return topologyDispatcher.disconnectNode(nodeId);
    }

    @Override public void setPeerContext(final Peer.PeerContext<CustomMessage> peerContext) {
        peerCtx = peerContext;
        peerContext.notifyPeers(new CustomMessage());
    }

    @Override public void handle(final CustomMessage msg) {
        // notifications from peers
    }

}