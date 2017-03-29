/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.util.Timeout;
import org.opendaylight.restconfsb.topology.RestconfTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

public interface ClusteredRestconfTopology extends RestconfTopology {
    /**
     * Register master mount point. Master mount point communicates directly with device.
     * @param context context
     * @param nodeId mount point id
     */
    void registerMasterMountPoint(ActorContext context, NodeId nodeId);

    /**
     * Register slave mount point. Slave mount point routes requests to master defined by {@link ActorRef}. Master ref
     * must point to {@link org.opendaylight.restconfsb.topology.cluster.impl.device.RestconfFacadeActor}
     * @param context context
     * @param nodeId mount point id
     * @param masterRef master facade actor ref
     */
    void registerSlaveMountPoint(ActorContext context, NodeId nodeId, ActorRef masterRef);

    /**
     * Unregisters mount point
     * @param nodeId node id
     */
    void unregisterMountPoint(NodeId nodeId);

    /**
     * @return actors ask timeout
     */
    Timeout getAskTimeout();
}
