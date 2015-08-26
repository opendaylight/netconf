/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;

import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;
import org.glassfish.jersey.servlet.ServletContainer;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.RestconfCORSFilter;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.Config;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.Delete;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.Get;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.Operational;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.Post;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.Put;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.RestConnectorRuntimeMXBean;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.Rpcs;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.core.api.Broker.ProviderSession;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYangBodyWriter;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYinBodyWriter;
import org.opendaylight.netconf.sal.rest.api.RestConnector;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.RestconfDocumentedExceptionMapper;
import org.opendaylight.netconf.sal.rest.impl.XmlNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
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

public class RestconfProviderImpl implements Provider, AutoCloseable, RestConnector, RestConnectorRuntimeMXBean {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfProviderImpl.class);
    private static final String REST_CONNECTOR_BASE_PATH = "/restconf";

    private final StatisticsRestconfServiceWrapper stats = StatisticsRestconfServiceWrapper.getInstance();
    private ListenerRegistration<SchemaContextListener> listenerRegistration;
    private final PortNumber port;
    private Thread webSocketServerThread;
    private final BundleContext bundleCx;

    /**
     * RestconfProviderImpl constructor
     *
     * @param bundleCx
     * @param port
     */
    public RestconfProviderImpl(@Nonnull final BundleContext bundleCx, @Nonnull final PortNumber port) {
        this.bundleCx = Preconditions.checkNotNull(bundleCx);
        this.port = Preconditions.checkNotNull(port);
        RestconfImpl.getInstance().setControllerContext(ControllerContext.getInstance());
    }

    @Override
    public void onSessionInitiated(final ProviderSession session) {
        LOG.debug("Rest_Connector_Bundle: The bundle is starting");
        Preconditions.checkState(( !session.isClosed()), "Session is closed.");

        final DOMDataBroker domDataBroker = session.getService(DOMDataBroker.class);

        BrokerFacade.getInstance().setContext(session);
        BrokerFacade.getInstance().setDomDataBroker( domDataBroker);
        final SchemaService schemaService = session.getService(SchemaService.class);
        listenerRegistration = schemaService.registerSchemaContextListener(ControllerContext.getInstance());
        BrokerFacade.getInstance().setRpcService(session.getService(DOMRpcService.class));


        ControllerContext.getInstance().setSchemas(schemaService.getGlobalContext());
        ControllerContext.getInstance().setMountService(session.getService(DOMMountPointService.class));

        ServiceReference<?> httpServiceReference = bundleCx.getServiceReference(HttpService.class.getName());
        HttpService httpService = (HttpService) bundleCx.getService(httpServiceReference);
        try {
            httpService.registerServlet(REST_CONNECTOR_BASE_PATH, restconfServletInit(), null, null);
        } catch (ServletException | NamespaceException e) {
            LOG.error("Rest_Connector_Bundle: unexpected error, restconf servlet was not registred", e);
            return;
        } finally {
            bundleCx.ungetService(httpServiceReference);
        }
        streamWebSocketInit();
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    @Override
    public void close() {

        if (listenerRegistration != null) {
            listenerRegistration.close();
        }

        WebSocketServer.destroyInstance();
        webSocketServerThread.interrupt();
    }

    @Override
    public Config getConfig() {
        final Config config = new Config();

        final Get get = new Get();
        get.setReceivedRequests(stats.getConfigGet());
        get.setSuccessfulResponses(stats.getSuccessGetConfig());
        get.setFailedResponses(stats.getFailureGetConfig());
        config.setGet(get);

        final Post post = new Post();
        post.setReceivedRequests(stats.getConfigPost());
        post.setSuccessfulResponses(stats.getSuccessPost());
        post.setFailedResponses(stats.getFailurePost());
        config.setPost(post);

        final Put put = new Put();
        put.setReceivedRequests(stats.getConfigPut());
        put.setSuccessfulResponses(stats.getSuccessPut());
        put.setFailedResponses(stats.getFailurePut());
        config.setPut(put);

        final Delete delete = new Delete();
        delete.setReceivedRequests(stats.getConfigDelete());
        delete.setSuccessfulResponses(stats.getSuccessDelete());
        delete.setFailedResponses(stats.getFailureDelete());
        config.setDelete(delete);

        return config;
    }

    @Override
    public Operational getOperational() {
        final BigInteger opGet = stats.getOperationalGet();
        final Operational operational = new Operational();
        final Get get = new Get();
        get.setReceivedRequests(opGet);
        get.setSuccessfulResponses(stats.getSuccessGetOperational());
        get.setFailedResponses(stats.getFailureGetOperational());
        operational.setGet(get);
        return operational;
    }

    @Override
    public Rpcs getRpcs() {
        final BigInteger rpcInvoke = stats.getRpc();
        final Rpcs rpcs = new Rpcs();
        rpcs.setReceivedRequests(rpcInvoke);
        return rpcs;
    }

    private ServletContainer restconfServletInit() {
        final ResourceConfig rc = new ResourceConfig();
        rc.setApplicationName("RestconfApplication");
        rc.register(new RestconfDocumentedExceptionMapper(), ExceptionMapper.class);
        rc.register(new JsonNormalizedNodeBodyReader(), MessageBodyReader.class);
        rc.register(new XmlNormalizedNodeBodyReader(), MessageBodyReader.class);
        rc.register(stats, StatisticsRestconfServiceWrapper.class);
        rc.register(NormalizedNodeJsonBodyWriter.class);
        rc.register(NormalizedNodeXmlBodyWriter.class);
        rc.register(SchemaExportContentYinBodyWriter.class);
        rc.register(SchemaExportContentYangBodyWriter.class);
        rc.register(RestconfCORSFilter.class);
        EncodingFilter.enableFor(rc, GZipEncoder.class);

        return new ServletContainer(rc);
    }

    private void streamWebSocketInit() {
        webSocketServerThread = new Thread(WebSocketServer.createInstance(port.getValue().intValue()));
        webSocketServerThread.setName("Web socket server on port " + port);
        webSocketServerThread.start();
    }
}