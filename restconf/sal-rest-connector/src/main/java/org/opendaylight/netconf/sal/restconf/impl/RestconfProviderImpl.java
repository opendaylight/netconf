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
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.core.api.Broker.ProviderSession;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.sal.rest.api.RestConnector;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

public class RestconfProviderImpl implements Provider, AutoCloseable, RestConnector, RestConnectorRuntimeMXBean {

    private final StatisticsRestconfServiceWrapper stats = StatisticsRestconfServiceWrapper.getInstance();
    private ListenerRegistration<SchemaContextListener> listenerRegistration;
    private Ipv4Address ip;
    private PortNumber port;
    private Thread webSocketServerThread;

    public void setWebsocketPort(final PortNumber port) {
        this.port = port;
    }

    public void setWebsocketAddress(final Ipv4Address ip) {
        this.ip = ip;
    }

    @Override
    public void onSessionInitiated(final ProviderSession session) {
        final DOMDataBroker domDataBroker = session.getService(DOMDataBroker.class);

        BrokerFacade.getInstance().setContext(session);
        BrokerFacade.getInstance().setDomDataBroker( domDataBroker);
        final SchemaService schemaService = session.getService(SchemaService.class);
        this.listenerRegistration = schemaService.registerSchemaContextListener(ControllerContext.getInstance());
        BrokerFacade.getInstance().setRpcService(session.getService(DOMRpcService.class));
        BrokerFacade.getInstance().setDomNotificationService(session.getService(DOMNotificationService.class));

        ControllerContext.getInstance().setSchemas(schemaService.getGlobalContext());
        ControllerContext.getInstance().setMountService(session.getService(DOMMountPointService.class));

        this.webSocketServerThread = new Thread(WebSocketServer.createInstance(this.ip.getValue(),
                                                                            this.port.getValue().intValue()));
        this.webSocketServerThread.setName("Web socket server on port " + this.port);
        this.webSocketServerThread.start();
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    @Override
    public void close() {

        if (this.listenerRegistration != null) {
            this.listenerRegistration.close();
        }

        WebSocketServer.destroyInstance();
        this.webSocketServerThread.interrupt();
    }

    @Override
    public Config getConfig() {
        final Config config = new Config();

        final Get get = new Get();
        get.setReceivedRequests(this.stats.getConfigGet());
        get.setSuccessfulResponses(this.stats.getSuccessGetConfig());
        get.setFailedResponses(this.stats.getFailureGetConfig());
        config.setGet(get);

        final Post post = new Post();
        post.setReceivedRequests(this.stats.getConfigPost());
        post.setSuccessfulResponses(this.stats.getSuccessPost());
        post.setFailedResponses(this.stats.getFailurePost());
        config.setPost(post);

        final Put put = new Put();
        put.setReceivedRequests(this.stats.getConfigPut());
        put.setSuccessfulResponses(this.stats.getSuccessPut());
        put.setFailedResponses(this.stats.getFailurePut());
        config.setPut(put);

        final Delete delete = new Delete();
        delete.setReceivedRequests(this.stats.getConfigDelete());
        delete.setSuccessfulResponses(this.stats.getSuccessDelete());
        delete.setFailedResponses(this.stats.getFailureDelete());
        config.setDelete(delete);

        return config;
    }

    @Override
    public Operational getOperational() {
        final BigInteger opGet = this.stats.getOperationalGet();
        final Operational operational = new Operational();
        final Get get = new Get();
        get.setReceivedRequests(opGet);
        get.setSuccessfulResponses(this.stats.getSuccessGetOperational());
        get.setFailedResponses(this.stats.getFailureGetOperational());
        operational.setGet(get);
        return operational;
    }

    @Override
    public Rpcs getRpcs() {
        final BigInteger rpcInvoke = this.stats.getRpc();
        final Rpcs rpcs = new Rpcs();
        rpcs.setReceivedRequests(rpcInvoke);
        return rpcs;
    }
}
