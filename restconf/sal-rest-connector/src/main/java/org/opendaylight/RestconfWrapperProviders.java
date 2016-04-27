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
import org.opendaylight.restconf.rest.RestConnectorProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;

public class RestconfWrapperProviders implements AutoCloseable, RestConnector {

    // DRAFT02
    RestconfProviderImpl providerDraft02;
    // DRAFT11
    RestConnectorProvider providerDraft11;

    public RestconfWrapperProviders(final PortNumber port) {
        // Init draft02 provider
        this.providerDraft02 = new RestconfProviderImpl();
        this.providerDraft02.setWebsocketPort(port);

        // Init draft11 provider
        this.providerDraft11 = new RestConnectorProvider(port);
    }

    public void registerProviders(final Broker broker) {
        // Register draft02 provider
        broker.registerProvider(this.providerDraft02);

        // Register draft11 provider
        broker.registerProvider(this.providerDraft11);
    }

    public RestConnectorRuntimeRegistration runtimeRegistration(
            final RestConnectorRuntimeRegistrator runtimeRegistration) {
        return runtimeRegistration.register(this.providerDraft02);
    }

    @Override
    public void close() throws Exception {
        this.providerDraft02.close();
        this.providerDraft11.close();
    }

}
