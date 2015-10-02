/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import akka.actor.TypedActor.Receiver;
import com.google.common.annotations.Beta;
import javax.annotation.Nonnull;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

@Beta
public interface TopologyManager extends NodeListener, Receiver, RemoteNodeListener{

    void notifyNodeStatusChange(NodeId nodeId);

    // DEBUG helper methods
    @Nonnull
    String getTopologyId();

    int getId();

    /* Add useful getters to retrieve nodes and the topology
    @Nonnull
    TA getTopology();

    @Nonnull
    TA getTopology(@Nonnull final LogicalDatastoreType type);

    @Nonnull
    NA getNode(@Nonnull final NodeId nodeId);

    @Nonnull
    NA getNode(@Nonnull final NodeId nodeId, @Nonnull final LogicalDatastoreType type);
    */

}
