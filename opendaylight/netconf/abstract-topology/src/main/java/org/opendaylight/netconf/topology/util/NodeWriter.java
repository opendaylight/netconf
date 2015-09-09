package org.opendaylight.netconf.topology.util;

import javax.annotation.Nonnull;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Augmentation;

public interface NodeWriter<NA extends Augmentation<Node>> {

    void init(@Nonnull final NodeId id, @Nonnull final NA operationalDataNode);

    void update(@Nonnull final NodeId id, @Nonnull final NA operationalDataNode);

    void delete(@Nonnull final NodeId id);

}
