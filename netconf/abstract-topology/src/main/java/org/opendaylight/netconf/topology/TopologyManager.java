/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import akka.actor.TypedActor.PostStop;
import akka.actor.TypedActor.PreStart;
import akka.actor.TypedActor.Receiver;
import com.google.common.annotations.Beta;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import scala.concurrent.Future;

/**
 * Top level topology manager that handles communication between nodes, aggregates results, and handles writes into the datastore
 */
@Beta
public interface TopologyManager extends NodeListener, Receiver, RemoteNodeListener, PreStart, PostStop {

    /**
     * ask if this manager is master
     * @return true/false based on ownership status
     */
    Future<Boolean> isMaster();

    /**
     *
     * @param nodeId - id of the node that sessionUp/Down happened on
     */
    void notifyNodeStatusChange(NodeId nodeId);

    boolean hasAllPeersUp();

}
