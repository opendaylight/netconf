package org.opendaylight.netconf.topology.example;

import javax.annotation.Nonnull;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingSalNodeWriter implements NodeWriter{

    private static final Logger LOG = LoggerFactory.getLogger(LoggingSalNodeWriter.class);

    @Override
    public void init(@Nonnull NodeId id, @Nonnull Node operationalDataNode) {
        LOG.warn("Init recieved");
        LOG.warn("NodeId: {}", id.getValue());
        LOG.warn("Node: {}", operationalDataNode);
    }

    @Override
    public void update(@Nonnull NodeId id, @Nonnull Node operationalDataNode) {
        LOG.warn("Update recieved");
        LOG.warn("NodeId: {}", id.getValue());
        LOG.warn("Node: {}", operationalDataNode);
    }

    @Override
    public void delete(@Nonnull NodeId id) {
        LOG.warn("Delete recieved");
        LOG.warn("NodeId: {}", id.getValue());
    }
}
