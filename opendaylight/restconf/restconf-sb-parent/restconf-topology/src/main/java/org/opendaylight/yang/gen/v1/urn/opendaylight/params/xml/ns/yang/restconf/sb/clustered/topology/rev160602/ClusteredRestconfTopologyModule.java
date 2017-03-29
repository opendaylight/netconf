package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.restconf.sb.clustered.topology.rev160602;

import org.opendaylight.restconfsb.communicator.impl.sender.NettyHttpClientProvider;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.restconfsb.communicator.impl.sender.TrustStore;

public class ClusteredRestconfTopologyModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.restconf.sb.clustered.topology.rev160602.AbstractClusteredRestconfTopologyModule {

    public ClusteredRestconfTopologyModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public ClusteredRestconfTopologyModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, final org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.restconf.sb.clustered.topology.rev160602.ClusteredRestconfTopologyModule oldModule, final java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final TrustStore trustStore = new TrustStore(getTruststorePath(), getTruststorePassword(), getTruststoreType(),
                getTruststorePathType());
        final SenderFactory senderFactory = new SenderFactory(new NettyHttpClientProvider(trustStore), getReconnectStreamsFail());
        return new org.opendaylight.restconfsb.topology.cluster.impl.ClusteredRestconfTopologyImpl(
                getBindingRegistryDependency(),
                getDomRegistryDependency(),
                senderFactory,
                getReconnectExecutorDependency(),
                getProcessingExecutorDependency(),
                getActorSystemProviderServiceDependency().getActorSystem(),
                getAkkaAskTimeout(),
                getEntityOwnershipServiceDependency());
    }

}
