/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import org.opendaylight.controller.md.sal.common.util.jmx.AbstractMXBean;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.api.RestConnector;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Config;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Delete;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Get;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Operational;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Post;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Put;
import org.opendaylight.netconf.sal.restconf.impl.jmx.RestConnectorRuntimeMXBean;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Rpcs;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

public class RestconfProviderImpl extends AbstractMXBean
        implements AutoCloseable, RestConnector, RestConnectorRuntimeMXBean {
    private final DOMDataBroker domDataBroker;
    private final DOMSchemaService schemaService;
    private final DOMRpcService rpcService;
    private final DOMNotificationService notificationService;
    private final DOMMountPointService mountPointService;
    private final PortNumber websocketPort;
    private final StatisticsRestconfServiceWrapper stats = StatisticsRestconfServiceWrapper.getInstance();
    private ListenerRegistration<SchemaContextListener> listenerRegistration;
    private Thread webSocketServerThread;

    public RestconfProviderImpl(final DOMDataBroker domDataBroker, final DOMSchemaService schemaService, final DOMRpcService rpcService,
            final DOMNotificationService notificationService, final DOMMountPointService mountPointService,
            final PortNumber websocketPort) {
        super("Draft02ProviderStatistics", "restconf-connector", null);
        this.domDataBroker = Preconditions.checkNotNull(domDataBroker);
        this.schemaService = Preconditions.checkNotNull(schemaService);
        this.rpcService = Preconditions.checkNotNull(rpcService);
        this.notificationService = Preconditions.checkNotNull(notificationService);
        this.mountPointService = Preconditions.checkNotNull(mountPointService);
        this.websocketPort = Preconditions.checkNotNull(websocketPort);
    }

    public void start() {
        this.listenerRegistration = schemaService.registerSchemaContextListener(ControllerContext.getInstance());

        BrokerFacade.getInstance().setDomDataBroker(domDataBroker);
        BrokerFacade.getInstance().setRpcService(rpcService);
        BrokerFacade.getInstance().setDomNotificationService(notificationService);

        ControllerContext.getInstance().setSchemas(schemaService.getGlobalContext());
        ControllerContext.getInstance().setMountService(mountPointService);

        this.webSocketServerThread = new Thread(WebSocketServer.createInstance(websocketPort.getValue().intValue()));
        this.webSocketServerThread.setName("Web socket server on port " + websocketPort);
        this.webSocketServerThread.start();

        registerMBean();
    }

    @Override
    public void close() {
        BrokerFacade.getInstance().setDomDataBroker(null);

        if (this.listenerRegistration != null) {
            this.listenerRegistration.close();
        }

        WebSocketServer.destroyInstance();
        if (this.webSocketServerThread != null) {
            this.webSocketServerThread.interrupt();
        }

        unregisterMBean();
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
