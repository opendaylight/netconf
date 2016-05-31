package org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.console.provider.impl.rev160323;

import org.opendaylight.netconf.console.impl.NetconfConsoleProvider;

public class NetconfConsoleProviderModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.console.provider.impl.rev160323.AbstractNetconfConsoleProviderModule {
    public NetconfConsoleProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NetconfConsoleProviderModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.console.provider.impl.rev160323.NetconfConsoleProviderModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final NetconfConsoleProvider provider = new NetconfConsoleProvider();
        getBrokerDependency().registerProvider(provider);
        return provider;
    }
}
