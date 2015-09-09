package org.opendaylight.netconf.topology;

import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nonnull;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Augmentation;

public interface ConnectionAdministrator<NA extends Augmentation<Node>> {

    @Nonnull ListenableFuture<NA> connect(@Nonnull NodeId nodeId, @Nonnull NA configNode);

    @Nonnull ListenableFuture<NA> update(@Nonnull NodeId nodeId, @Nonnull NA configNode);

    @Nonnull ListenableFuture<Void> delete(@Nonnull NodeId nodeId);
}
