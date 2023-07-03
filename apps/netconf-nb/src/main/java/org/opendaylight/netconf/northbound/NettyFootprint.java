/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

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
@Component(service = NettyFootprint.class, configurationPid = "org.opendaylight.netconf.northbound.netty")
@Designate(ocd = NettyFootprint.Configuration.class)
public final class NettyFootprint implements AutoCloseable {
    /**
     * Configuration of the NETCONF northbound.
     */
    @ObjectClassDefinition()
    public @interface Configuration {
        @AttributeDefinition(name = "Number of Netty boss threads", min = "0")
        int netty$_$boss$_$threads() default 0;

        @AttributeDefinition(name = "Number of Netty worker threads", min = "0")
        int netty$_$worker$_$threads() default 0;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NettyFootprint.class);

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    @Inject
    public NettyFootprint() {
        this(0, 0);
    }

    @Activate
    public NettyFootprint(final Configuration configuration) {
        this(configuration.netty$_$boss$_$threads(), configuration.netty$_$worker$_$threads());
    }

    public NettyFootprint(final int nettyBossThreads, final int nettyWorkerThreads) {
        bossGroup = NettyTransportSupport.newEventLoopGroup("odl-netconf-nb-boss", nettyBossThreads);
        workerGroup = NettyTransportSupport.newEventLoopGroup("odl-netconf-nb-worker", nettyWorkerThreads);
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

    EventLoopGroup bossGroup() {
        return bossGroup;
    }

    EventLoopGroup workerGroup() {
        return workerGroup;
    }
}
