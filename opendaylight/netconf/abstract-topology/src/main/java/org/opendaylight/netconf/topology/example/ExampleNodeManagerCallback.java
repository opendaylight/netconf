/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import akka.actor.ActorRef;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeFields;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

public class ExampleNodeManagerCallback implements NodeManagerCallback {

    public ExampleNodeManagerCallback() {
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

    @Nonnull @Override public ListenableFuture<Node> onNodeCreated(@Nonnull final NodeId nodeId,
                                                                   @Nonnull final Node configNode) {
        return Futures.immediateFuture(new NodeBuilder().addAugmentation(NetconfNode.class,
                new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connected).build()).build());
    }

    @Nonnull @Override public ListenableFuture<Node> onNodeUpdated(@Nonnull final NodeId nodeId,
                                                                   @Nonnull final Node configNode) {
        // update magic
        return Futures.immediateFuture(new NodeBuilder().addAugmentation(NetconfNode.class,
                new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connected).build()).build());
    }

    @Nonnull @Override public ListenableFuture<Void> onNodeDeleted(@Nonnull final NodeId nodeId) {
        return Futures.immediateFuture(null);
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> getCurrentStatusForNode(@Nonnull NodeId nodeId) {
        return null;
    }

    @Override
    public void onReceive(Object message, ActorRef sender) {

    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {

    }
}
