package org.opendaylight.netconf.topology;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import scala.concurrent.Future;

public interface TopologyActor {

    Future<Node> nodeCreated(NodeId nodeId, Node node);

    Future<Node> nodeUpdated(NodeId nodeId, Node node);

    Future<Node> nodeDeleted(NodeId nodeId);
}
