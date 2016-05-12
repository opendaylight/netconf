package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.spi.datastore.txprovider.rev141210;
import org.opendaylight.distributed.tx.spiimpl.DataStoreTxProvider;

public class DataStoreTxProviderModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.spi.datastore.txprovider.rev141210.AbstractDataStoreTxProviderModule {
    public DataStoreTxProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public DataStoreTxProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.spi.datastore.txprovider.rev141210.DataStoreTxProviderModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final DataStoreTxProvider txProviderDataStore = new DataStoreTxProvider();
        getBrokerDependency().registerConsumer(txProviderDataStore);
        return txProviderDataStore;
    }
}
