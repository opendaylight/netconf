package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.provider.rev141210;

import org.opendaylight.distributed.tx.it.provider.DistributedTXItProvider;

public class DistributedTxItProviderModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.provider.rev141210.AbstractDistributedTxItProviderModule {
    public DistributedTxItProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public DistributedTxItProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.provider.rev141210.DistributedTxItProviderModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        DistributedTXItProvider provider = new DistributedTXItProvider(getDtxProviderDependency());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }
}
