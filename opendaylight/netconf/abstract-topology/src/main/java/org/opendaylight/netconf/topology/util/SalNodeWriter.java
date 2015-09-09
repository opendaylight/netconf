package org.opendaylight.netconf.topology.util;

import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Augmentation;

public final class SalNodeWriter<NA extends Augmentation<Node>> implements NodeWriter<NA> {

    private final DataBroker dataBroker;

    public SalNodeWriter(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override public void init(@Nonnull final NodeId id, @Nonnull final NA operationalDataNode) {
        // put into Datastore
    }

    @Override public void update(@Nonnull final NodeId id, @Nonnull final NA operationalDataNode) {
        // merge
    }

    @Override public void delete(@Nonnull final NodeId id) {
        // delete
    }
}
