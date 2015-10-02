/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import akka.actor.ActorContext;
import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

@Beta
public interface TopologyManagerCallback<M> extends Peer<TopologyManager<M>>, RoleChangeListener {

    ListenableFuture<Node> nodeCreated(ActorContext context, NodeId nodeId, Node node);

    ListenableFuture<Node> nodeUpdated(NodeId nodeId, Node node);

    ListenableFuture<Void> nodeDeleted(NodeId nodeId);

    interface TopologyManagerCallbackFactory<M> {
        TopologyManagerCallback<M> create(TopologyManager<M> topologyParent);
    }
}
