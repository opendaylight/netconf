package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.rest.connector.rev160316;

import org.opendaylight.restconf.rest.RestConnectorProvider;

public class RestConnectorModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.rest.connector.rev160316.AbstractRestConnectorModule {
    public RestConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public RestConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, final org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.rest.connector.rev160316.RestConnectorModule oldModule, final java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final RestConnectorProvider instance = new RestConnectorProvider(getWebsocketPort());
        getDomBrokerDependency().registerProvider(instance);

        return instance;
    }

}
