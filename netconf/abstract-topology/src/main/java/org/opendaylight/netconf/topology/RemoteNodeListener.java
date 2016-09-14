/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import com.google.common.annotations.Beta;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import scala.concurrent.Future;

/**
 * Interface that provides methods of calling node events on a remote actor.
 * Use these when you want to call node events asynchronously similar to akka ask()
 */
@Beta
public interface RemoteNodeListener {

    /**
     * This is called when a remote node is informing you that a new configuration was recieved.
     * @param message - serializable message to send
     * @return response from the remote node
     */
    Future<NormalizedNodeMessage> onRemoteNodeCreated(NormalizedNodeMessage message);

    /**
     * This is called when a remote node is informing you that a new configuration was deleted.
     * @param nodeId - id of the node which was deleted
     * @return void future success if delete succeed, failure otherwise
     */
    Future<Void> onRemoteNodeDeleted(NodeId nodeId);

    /**
     * Called when a remote node is requesting a node's status, after a status change notification(f.ex sessionUp, sessionDown)
     * on lower level
     * @param nodeId - id of the node which status we want to retrieve
     * @return status for the node requested
     */
    Future<NormalizedNodeMessage> remoteGetCurrentStatusForNode(NodeId nodeId);
}
