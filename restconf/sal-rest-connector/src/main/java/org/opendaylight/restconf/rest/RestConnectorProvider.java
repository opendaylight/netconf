/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest;

import java.util.Collection;
import java.util.Collections;
import org.opendaylight.controller.sal.core.api.Broker.ProviderSession;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.restconf.rest.api.connector.RestConnector;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Preconditions;

public class RestConnectorProvider implements Provider, RestConnector, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestConnectorProvider.class);

    private final PortNumber port;

    private Thread webSocketServerThread;
    private ListenerRegistration<SchemaContextListener> registerSchemaContextListener;

    public RestConnectorProvider(final PortNumber port) {
        this.port = port;
    }

    @Override
    public void onSessionInitiated(final ProviderSession session) {
        final SchemaService schemaService = Preconditions.checkNotNull(session.getService(SchemaService.class));

        final BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        final ServiceReference<?> serviceReference = bundleContext
                .getServiceReference(RestconfApplicationService.class.getName());
        final RestconfApplication restSchemaController = (RestconfApplication) bundleContext
                .getService(serviceReference);
        final RestSchemaController restConnector = restSchemaController.getRestConnector();
        restConnector.setGlobalSchema(schemaService.getGlobalContext());

        this.registerSchemaContextListener = schemaService.registerSchemaContextListener(restConnector);
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
    }

    private void streamWebSocketInit() {
        this.webSocketServerThread = new Thread(WebSocketServer.createInstance(this.port.getValue().intValue()));
        this.webSocketServerThread.setName("Web socket server on port " + this.port);
        this.webSocketServerThread.start();
    }
}
