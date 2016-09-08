/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight;

import org.opendaylight.controller.config.yang.md.sal.rest.connector.RestConnectorRuntimeRegistration;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.RestConnectorRuntimeRegistrator;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.sal.rest.api.RestConnector;
import org.opendaylight.netconf.sal.restconf.impl.RestconfProviderImpl;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;

/**
 * Wrapping providers from restconf draft02 and draft16.
 *
 */
public class RestconfWrapperProviders implements AutoCloseable, RestConnector {

    // DRAFT02
    private final RestconfProviderImpl providerDraft02;
    // DRAFT16
    private final RestConnectorProvider providerDraft16;

    /**
     * Init both providers:
     * <ul>
     * <li>draft02 - {@link RestconfProviderImpl}
     * <li>draft16 - {@link RestConnectorProvider}
     * </ul>
     *
     * @param port
     *            - port for web sockets in provider for draft02
     */
    public RestconfWrapperProviders(final PortNumber port) {
        // Init draft02 provider
        this.providerDraft02 = new RestconfProviderImpl();
        this.providerDraft02.setWebsocketPort(port);

        this.providerDraft16 = new RestConnectorProvider();
    }

    /**
     * Register both providers, which will use the SAL layer:
     * <ul>
     * <li>draft02 - {@link RestconfProviderImpl}
     * <li>draft16 - {@link RestConnectorProvider}
     * </ul>
     *
     * @param broker
     *            - {@link Broker}
     */
    public void registerProviders(final Broker broker) {
        // Register draft02 provider
        broker.registerProvider(this.providerDraft02);

        // Register draft16 provider
        broker.registerProvider(this.providerDraft16);
    }

    /**
     * Register runtime beans from restconf draft02 {@link RestconfProviderImpl}
     *
     * @param runtimeRegistration
     *            - for register runtime beans
     * @return {@link RestConnectorRuntimeRegistration}
     */
    public RestConnectorRuntimeRegistration runtimeRegistration(
            final RestConnectorRuntimeRegistrator runtimeRegistration) {
        return runtimeRegistration.register(this.providerDraft02);
    }

    @Override
    public void close() throws Exception {
        this.providerDraft02.close();
        this.providerDraft16.close();
    }

}
