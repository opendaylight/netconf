package org.opendaylight.netconf.topology.impl;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import org.opendaylight.netconf.topology.TopologyActor;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import scala.concurrent.Future;

public class TopologyActorImpl implements TopologyActor{

    private final TypedActorExtension typedActorExtension;

    public TopologyActorImpl(final ActorSystem actorSystem) {
        this.typedActorExtension = TypedActor.get(actorSystem);

    }

    @Override
    public Future<Node> nodeCreated(NodeId nodeId, Node node) {
        return null;
    }

    @Override
    public Future<Node> nodeUpdated(NodeId nodeId, Node node) {
        return null;
    }

    @Override
    public Future<Node> nodeDeleted(NodeId nodeId) {
        return null;
    }
}
