package org.opendaylight.controller.config.yang.md.sal.rest.connector;

import org.opendaylight.netconf.sal.restconf.impl.RestconfProviderImpl;
import org.osgi.framework.BundleContext;

import com.google.common.base.Preconditions;


public class RestConnectorModule extends org.opendaylight.controller.config.yang.md.sal.rest.connector.AbstractRestConnectorModule {

    private static RestConnectorRuntimeRegistration runtimeRegistration;
    private BundleContext bundleContext;

    public RestConnectorModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public RestConnectorModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, BundleContext bundleContext) {
        super(identifier, dependencyResolver);
        this.setBundleContext(bundleContext);
    }

    public RestConnectorModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.md.sal.rest.connector.RestConnectorModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    public RestConnectorModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.md.sal.rest.connector.RestConnectorModule oldModule, java.lang.AutoCloseable oldInstance, BundleContext bundleContext) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
        this.setBundleContext(bundleContext);
    }

    @Override
    public void customValidation() {
        Preconditions.checkArgument(bundleContext != null, "BundleContext was not properly set up!");
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // Create an instance of our provider
        RestconfProviderImpl instance = new RestconfProviderImpl(bundleContext, getWebsocketPort());
        // Set its port
        instance.setWebsocketPort(getWebsocketPort());
        // Register it with the Broker
        getDomBrokerDependency().registerProvider(instance);

        if(runtimeRegistration != null){
            runtimeRegistration.close();
        }

        runtimeRegistration =
            getRootRuntimeBeanRegistratorWrapper().register(instance);

        return instance;
    }

    private void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
}

