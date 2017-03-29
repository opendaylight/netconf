package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.restconf.sb.topology.rev160602;

import org.opendaylight.restconfsb.communicator.impl.sender.NettyHttpClientProvider;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.restconfsb.communicator.impl.sender.TrustStore;
import org.opendaylight.restconfsb.topology.BaseRestconfTopology;
import org.opendaylight.restconfsb.topology.RestconfTopologyProvider;

public class RestconfTopologyModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.restconf.sb.topology.rev160602.AbstractRestconfTopologyModule {

    public RestconfTopologyModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public RestconfTopologyModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, final org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.restconf.sb.topology.rev160602.RestconfTopologyModule oldModule, final java.lang.AutoCloseable oldInstance) {
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
        final RestconfTopologyProvider provider = new RestconfTopologyProvider();
        getDomRegistryDependency().registerProvider(provider);
        getBindingRegistryDependency().registerProvider(provider);
        return new BaseRestconfTopology(senderFactory, getProcessingExecutorDependency(),
                getReconnectExecutorDependency(), provider.getDataBroker(), provider.getMountPointService());
    }

}
