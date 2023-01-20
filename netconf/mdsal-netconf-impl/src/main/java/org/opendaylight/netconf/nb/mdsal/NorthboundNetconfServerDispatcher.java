/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nb.mdsal;

import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;
import org.opendaylight.netconf.api.NetconfServerDispatcher;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.impl.NetconfServerDispatcherImpl;
import org.opendaylight.netconf.impl.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.impl.ServerChannelInitializer;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.impl.osgi.NetconfMonitoringServiceImpl;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Noth-bound {@link NetconfServerDispatcher}.
 */
@Component(service = NetconfServerDispatcher.class, property = "type=netconf-server-dispatcher")
public final class NorthboundNetconfServerDispatcher extends NetconfServerDispatcherImpl {
    @Activate
    public NorthboundNetconfServerDispatcher(
        @Reference(target = "type=global-boss-group") final EventLoopGroup bossGroup,
        @Reference(target = "type=global-worker-group") final EventLoopGroup workerGroup,
        @Reference(target = "type=mapper-aggregator-registry") final NetconfOperationServiceFactoryListener registry,
        final Timer timer, final NetconfOperationServiceFactory netconfOperationProvider,
        final NetconfMonitoringService monitoringService, final long connectionTimeoutMillis) {
        super(
            new ServerChannelInitializer(new NetconfServerSessionNegotiatorFactory(timer, netconfOperationProvider,
                new SessionIdProvider(), connectionTimeoutMillis,
                new NetconfMonitoringServiceImpl(netconfOperationProvider))),
            bossGroup, workerGroup);
    }

    @Activate
    public NorthboundNetconfServerDispatcher(
            @Reference(target = "type=global-boss-group") final EventLoopGroup bossGroup,
            @Reference(target = "type=global-worker-group") final EventLoopGroup workerGroup,
            @Reference(target = "type=global-timer") final Timer timer,
            @Reference(target = "type=netconf-server-monitoring") final NetconfMonitoringService monitoringService,
            final Configuration configuration) {
        super(new ServerChannelInitializer(
            new NetconfServerSessionNegotiatorFactory(timer, null,
                new SessionIdProvider(), configuration.connectionTimeoutMillis(), monitoringService)),
            bossGroup, workerGroup);
    }
}
