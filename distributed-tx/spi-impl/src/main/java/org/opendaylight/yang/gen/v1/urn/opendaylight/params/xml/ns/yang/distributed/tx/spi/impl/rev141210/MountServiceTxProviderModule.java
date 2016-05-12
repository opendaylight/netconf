package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.spi.impl.rev141210;

import org.opendaylight.distributed.tx.spiimpl.MountServiceTxProvider;

public class MountServiceTxProviderModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.spi.impl.rev141210.AbstractMountServiceTxProviderModule {
    public MountServiceTxProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public MountServiceTxProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.spi.impl.rev141210.MountServiceTxProviderModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final MountServiceTxProvider txProviderMountPoint = new MountServiceTxProvider();
        getBrokerDependency().registerConsumer(txProviderMountPoint);
        return txProviderMountPoint;
    }

}
