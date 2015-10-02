package org.opendaylight.netconf.topology;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import scala.concurrent.Future;

/**
 * Interface that provides methods of calling node events on a remote actor.
 * Use these when you want to call node events asynchronously similar to akka ask()
 */
public interface RemoteNodeListener {

    /**
     * This is called when a remote node is informing you that a new configuration was recieved.
     * @param nodeId
     * @param node
     * @return
     */
    Future<Node> remoteNodeCreated(NodeId nodeId, Node node);

    /**
     * This is called when a remote node is informing you that a configuration was updated.
     * @param nodeId
     * @param node
     * @return
     */
    Future<Node> remoteNodeUpdated(NodeId nodeId, Node node);

    /**
     * This is called when a remote node is informing you that a new configuration was deleted.
     * @param nodeId
     * @return
     */
    Future<Void> remoteNodeDeleted(NodeId nodeId);
}
