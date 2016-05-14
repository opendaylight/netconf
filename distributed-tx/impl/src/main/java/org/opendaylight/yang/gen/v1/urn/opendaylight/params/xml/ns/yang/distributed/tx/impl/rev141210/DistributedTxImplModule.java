package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.impl.rev141210;

import org.opendaylight.distributed.tx.impl.DTXProviderService;

public class DistributedTxImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.impl.rev141210.AbstractDistributedTxImplModule {
    public DistributedTxImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public DistributedTxImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.impl.rev141210.DistributedTxImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        return new DTXProviderService(getTxProviderDependency(), getDataStoreTxProviderDependency());
    }
}
