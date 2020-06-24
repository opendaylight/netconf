/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.tls.NetconfCallHomeTlsServer;
import org.opendaylight.netconf.callhome.protocol.tls.NetconfCallHomeTlsServerBuilder;
import org.opendaylight.netconf.callhome.protocol.tls.TlsAllowedDevicesMonitor;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfCallHomeTlsService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeTlsService.class);

    private Configuration config;
    private String topologyId;
    private DataBroker dataBroker;
    private SslHandlerFactory sslHandlerFactory;
    private ScheduledThreadPool keepaliveExecutor;
    private ThreadPool processingExecutor;
    private NetconfCallHomeTlsServer server;
    private CallHomeNetconfSubsystemListener subsystemListener;
    private TlsAllowedDevicesMonitor allowedDevicesMonitor;

    public NetconfCallHomeTlsService(final Configuration config,
                                     final DataBroker dataBroker,
                                     final String topologyId,
                                     final EventExecutor eventExecutor,
                                     final ScheduledThreadPool keepaliveExecutor,
                                     final ThreadPool processingExecutor,
                                     CallHomeNetconfSubsystemListener subsystemListener) {
        this.config = config;
        this.dataBroker = dataBroker;
        this.topologyId = topologyId;
        this.sslHandlerFactory = new SslHandlerFactoryAdapter(dataBroker);
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = processingExecutor;
        this.subsystemListener = subsystemListener;
        this.allowedDevicesMonitor = new TlsAllowedDevicesMonitorImpl(dataBroker);
    }

    public void init() {
        LOG.info("Initializing Call Home TLS server instance");

        NetconfCallHomeTlsServerBuilder builder = new NetconfCallHomeTlsServerBuilder();
        server = builder.setHost(config.getHost())
            .setPort(config.getPort())
            .setTimeout(config.getTimeout())
            .setMaxConnections(config.getMaxConnections())
            .setSslHandlerFactory(sslHandlerFactory)
            .setSubsystemListener(subsystemListener)
            .setTlsAllowedDevicesMonitor(allowedDevicesMonitor)
            .build();
        server.start();

        LOG.info("Initializing Call Home TLS server instance completed successfuly");
    }

    @Override
    public void close() {
        server.stop();
    }
}