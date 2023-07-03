/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component provides Netty thread group instances for NETCONF northbound servers.
 *
 * <p> The artifact substitutes the thread groups provided via
 * org.opendaylight.controller:netty-threadgroup-config module (delivered via
 * org.opendaylight.controller:odl-controller-exp-netty-config) because of
 * incompatibility with new netconf transport implementation. Same configuration
 * (configurationPid) is used as before.
 */
@Singleton
@Component(service = NettyThreads.class, configurationPid = "org.opendaylight.netconf.northbound.netty")
@Designate(ocd = NettyThreads.Configuration.class)
public final class NettyThreads implements AutoCloseable {
    /**
     * Configuration of the NETCONF northbound.
     */
    @ObjectClassDefinition()
    public @interface Configuration {
        @AttributeDefinition(name = "Number of Netty boss threads", min = "0")
        int boss$_$threads() default 0;

        @AttributeDefinition(name = "Number of Netty worker threads", min = "0")
        int worker$_$threads() default 0;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NettyThreads.class);

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    @Inject
    public NettyThreads() {
        this(0, 0);
    }

    @Activate
    public NettyThreads(final Configuration configuration) {
        this(configuration.boss$_$threads(), configuration.worker$_$threads());
    }

    public NettyThreads(final int bossThreads, final int workerThreads) {
        bossGroup = NettyTransportSupport.newEventLoopGroup("odl-netconf-nb-boss", bossThreads);
        workerGroup = NettyTransportSupport.newEventLoopGroup("odl-netconf-nb-worker", workerThreads);
        LOG.info("NETCONF Northbound Netty context initialized");
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        LOG.info("NETCONF Northbound Netty context shutting down");
        LOG.debug("Shutting down NETCONF Northbound boss thread group");
        bossGroup.shutdownGracefully();
        LOG.debug("Shutting down NETCONF Northbound worker thread group");
        workerGroup.shutdownGracefully();
        LOG.info("NETCONF Northbound Netty context shut down");
    }

    ServerBootstrap newServerBootstrap() {
        return NettyTransportSupport.newServerBootstrap().group(bossGroup, workerGroup);
    }
}
