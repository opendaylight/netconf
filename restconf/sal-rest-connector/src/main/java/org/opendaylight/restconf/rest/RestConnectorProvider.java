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
import javax.servlet.ServletException;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.opendaylight.controller.sal.core.api.Broker.ProviderSession;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.restconf.rest.api.connector.RestConnector;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.api.services.ServicesWrapper;
import org.opendaylight.restconf.rest.impl.connector.RestSchemaControllerImpl;
import org.opendaylight.restconf.rest.impl.services.ServicesWrapperImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Preconditions;

public class RestConnectorProvider implements Provider, RestConnector, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestConnectorProvider.class);
    private static final String REST_CONNECTOR_BASE_PATH = "/v09/restconf";

    private final BundleContext bundleCx;
    private final PortNumber port;

    private Thread webSocketServerThread;
    private HttpService httpService;
    private ListenerRegistration<SchemaContextListener> registerSchemaContextListener;

    public RestConnectorProvider(final BundleContext bundleCx, final PortNumber port) {
        this.bundleCx = bundleCx;
        this.port = port;
    }

    @Override
    public void onSessionInitiated(final ProviderSession session) {
        LOG.debug("REST_CONNECTOR_BUNDLE: BUNDLE IS STARTING");
        final SchemaService schemaService = Preconditions.checkNotNull(session.getService(SchemaService.class));
        final RestSchemaController restSchemaController = new RestSchemaControllerImpl();
        restSchemaController.setGlobalSchema(schemaService.getGlobalContext());

        final ServiceReference<?> serviceReference = this.bundleCx.getServiceReference(HttpService.class.getName());
        this.httpService = (HttpService) this.bundleCx.getService(serviceReference);
        LOG.info("REST_CONNECTOR_BUNDLE: HTTP SERVICE = " + this.httpService.toString());

         try {
            this.httpService.registerServlet(REST_CONNECTOR_BASE_PATH, restconfServletInit(restSchemaController), null,
                    null);
         } catch (ServletException | NamespaceException e) {
            LOG.error("REST_CONNECTOR BUNDLE: unexpected error, restconf servlet was not registred", e);
         return;
         }
        this.registerSchemaContextListener = schemaService.registerSchemaContextListener(restSchemaController);
        streamWebSocketInit();

        LOG.info("REST_CONNECTOR_BUNDLE: restconf servlet registered");
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
    }

    private ServletContainer restconfServletInit(final RestSchemaController restSchemaController) {
        final ServicesWrapper wrapper = new ServicesWrapperImpl(restSchemaController);
        final ResourceConfig rc = new ResourceConfig();
        rc.setApplicationName("Restconf");

        return new ServletContainer(rc);
    }

    private void streamWebSocketInit() {
        this.webSocketServerThread = new Thread(WebSocketServer.createInstance(this.port.getValue().intValue()));
        this.webSocketServerThread.setName("Web socket server on port " + this.port);
        this.webSocketServerThread.start();
    }
}
