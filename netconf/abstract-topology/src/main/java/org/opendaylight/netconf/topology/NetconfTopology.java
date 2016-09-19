/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.topology.impl.ConnectionStatusListenerRegistration;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public interface NetconfTopology {

    String getTopologyId();

    DataBroker getDataBroker();

    ListenableFuture<NetconfDeviceCapabilities> connectNode(NodeId nodeId, Node configNode);

    ListenableFuture<Void> disconnectNode(NodeId nodeId);

    /**
     * register master mount point
     * @param context
     * @param nodeId
     */
    void registerMountPoint(ActorContext context, NodeId nodeId);

    /**
     * register slave mountpoint with the provided ActorRef
     * @param context
     * @param nodeId
     * @param masterRef
     */
    void registerMountPoint(ActorContext context, NodeId nodeId, ActorRef masterRef);

    void unregisterMountPoint(NodeId nodeId);

    ConnectionStatusListenerRegistration registerConnectionStatusListener(NodeId node, RemoteDeviceHandler<NetconfSessionPreferences> listener);
}
