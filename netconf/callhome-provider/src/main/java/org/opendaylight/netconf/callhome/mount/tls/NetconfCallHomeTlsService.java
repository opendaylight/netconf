/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import io.netty.channel.EventLoopGroup;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netconf.callhome.mount.CallhomeStatusReporter;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.StatusRecorder;
import org.opendaylight.netconf.callhome.protocol.tls.NetconfCallHomeTlsServer;
import org.opendaylight.netconf.callhome.protocol.tls.NetconfCallHomeTlsServerBuilder;
import org.opendaylight.netconf.callhome.protocol.tls.TlsAllowedDevicesMonitor;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfCallHomeTlsService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeTlsService.class);

    private final Configuration config;
    private final SslHandlerFactory sslHandlerFactory;
    private NetconfCallHomeTlsServer server;
    private final CallHomeNetconfSubsystemListener subsystemListener;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final TlsAllowedDevicesMonitor allowedDevicesMonitor;
    private final StatusRecorder statusRecorder;

    public NetconfCallHomeTlsService(final Configuration config,
                                     final DataBroker dataBroker,
                                     final CallHomeNetconfSubsystemListener subsystemListener,
                                     final EventLoopGroup bossGroup,
                                     final EventLoopGroup workerGroup) {
        this.config = config;
        this.subsystemListener = subsystemListener;
        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;
        this.allowedDevicesMonitor = new TlsAllowedDevicesMonitorImpl(dataBroker);
        this.sslHandlerFactory = new SslHandlerFactoryAdapter(dataBroker, allowedDevicesMonitor);
        this.statusRecorder = new CallhomeStatusReporter(dataBroker);
    }

    public void init() {
        LOG.info("Initializing Call Home TLS server instance");

        final NetconfCallHomeTlsServerBuilder builder = new NetconfCallHomeTlsServerBuilder();
        server = builder.setHost(config.getHost())
            .setPort(config.getPort())
            .setTimeout(config.getTimeout())
            .setMaxConnections(config.getMaxConnections())
            .setSslHandlerFactory(sslHandlerFactory)
            .setSubsystemListener(subsystemListener)
            .setBossGroup(bossGroup)
            .setWorkerGroup(workerGroup)
            .setAllowedDevicesMonitor(allowedDevicesMonitor)
            .setStatusRecorder(statusRecorder)
            .build();
        server.start();

        LOG.info("Initializing Call Home TLS server instance completed successfuly");
    }

    @Override
    public void close() {
        server.stop();
        allowedDevicesMonitor.close();
    }
}