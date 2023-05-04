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
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.tls.NetconfCallHomeTlsServer;
import org.opendaylight.netconf.callhome.protocol.tls.NetconfCallHomeTlsServerBuilder;
import org.opendaylight.netconf.callhome.protocol.tls.TlsAllowedDevicesMonitor;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { })
public class NetconfCallHomeTlsService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeTlsService.class);

    private final NetconfCallHomeTlsServer server;

    @Activate
    public NetconfCallHomeTlsService(@Reference final SslHandlerFactoryProvider sslHandlerFactoryProvider,
            @Reference final TlsAllowedDevicesMonitor allowedDevicesMonitor,
            @Reference final CallHomeNetconfSubsystemListener subsystemListener,
            @Reference(target = "(type=global-boss-group)") final EventLoopGroup bossGroup,
            @Reference(target = "(type=global-worker-group)") final EventLoopGroup workerGroup) {
        this(sslHandlerFactoryProvider, allowedDevicesMonitor, subsystemListener, bossGroup, workerGroup,
            // FIXME: tie together with OSGi Config Admin
            defaultTlsConfiguration());
    }

    public NetconfCallHomeTlsService(final SslHandlerFactoryProvider sslHandlerFactoryProvider,
                                     final TlsAllowedDevicesMonitor allowedDevicesMonitor,
                                     final CallHomeNetconfSubsystemListener subsystemListener,
                                     final EventLoopGroup bossGroup,
                                     final EventLoopGroup workerGroup, final Configuration config) {
        LOG.info("Initializing Call Home TLS server instance");
        server = new NetconfCallHomeTlsServerBuilder()
            .setHost(config.getHost())
            .setPort(config.getPort())
            .setTimeout(config.getTimeout())
            .setMaxConnections(config.getMaxConnections())
            .setAllowedDevicesMonitor(requireNonNull(allowedDevicesMonitor))
            .setSslHandlerFactory(new SslHandlerFactoryAdapter(sslHandlerFactoryProvider.getSslHandlerFactory(null),
                allowedDevicesMonitor))
            .setSubsystemListener(requireNonNull(subsystemListener))
            .setBossGroup(requireNonNull(bossGroup))
            .setWorkerGroup(requireNonNull(workerGroup))
            .build();
        server.start();

        LOG.info("Initializing Call Home TLS server instance completed successfuly");
    }

    private static Configuration defaultTlsConfiguration() {
        final var conf = new Configuration();
        conf.setHost("0.0.0.0");
        conf.setPort(4335);
        conf.setTimeout(10_000);
        conf.setMaxConnections(64);
        return conf;
    }

    @Deactivate
    @Override
    public void close() {
        server.stop();
    }
}