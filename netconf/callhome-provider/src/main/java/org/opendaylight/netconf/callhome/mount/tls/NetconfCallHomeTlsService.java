/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import static java.util.Objects.requireNonNull;

import io.netty.channel.EventLoopGroup;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.tls.NetconfCallHomeTlsServer;
import org.opendaylight.netconf.callhome.protocol.tls.NetconfCallHomeTlsServerBuilder;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfCallHomeTlsService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeTlsService.class);

    private final Configuration config;
    private final SslHandlerFactory sslHandlerFactory;
    private final CallHomeNetconfSubsystemListener subsystemListener;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    private NetconfCallHomeTlsServer server;

    public NetconfCallHomeTlsService(final Configuration config,
                                     final DataBroker dataBroker,
                                     final CallHomeNetconfSubsystemListener subsystemListener,
                                     final EventLoopGroup bossGroup,
                                     final EventLoopGroup workerGroup) {
        this.config = requireNonNull(config);
        this.subsystemListener = requireNonNull(subsystemListener);
        this.bossGroup = requireNonNull(bossGroup);
        this.workerGroup = requireNonNull(workerGroup);
        this.sslHandlerFactory = new SslHandlerFactoryAdapter(dataBroker);
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
            .build();
        server.start();

        LOG.info("Initializing Call Home TLS server instance completed successfuly");
    }

    @Override
    public void close() {
        server.stop();
    }
}