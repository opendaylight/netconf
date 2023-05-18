/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import io.netty.channel.EventLoopGroup;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Component provides Netty thread group instances for NETCONF northbound servers.
 *
 * <p> The artifact substitutes the thread groups provided via
 * org.opendaylight.controller:netty-threadgroup-config module (delivered via
 * org.opendaylight.controller:odl-controller-exp-netty-config) because of
 * incompatibility with new netconf transport implementation. Same configuration
 * is used with addition of group names.
 */
@Component(service = OSGiNetconfThreadGroups.class, configurationPid = "org.opendaylight.netty.threadgroup")
@Designate(ocd = OSGiNetconfThreadGroups.Configuration.class)
public class OSGiNetconfThreadGroups {

    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(name = "global-boss-group-name")
        String bossGroupName() default "netconf-boss-group";

        @AttributeDefinition(name = "global-boss-group-thread-count")
        int bossThreadCount() default 0;

        @AttributeDefinition(name = "global-worker-group-name")
        String workerGroupName() default "netconf-worker-group";

        @AttributeDefinition(name = "global-worker-group-thread-count")
        int workerThreadCount() default 0;
    }

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    @Activate
    public OSGiNetconfThreadGroups(final Configuration configuration) {
        bossGroup = buildEventLoopGroup(configuration.bossGroupName(), configuration.bossThreadCount());
        workerGroup = buildEventLoopGroup(configuration.workerGroupName(), configuration.workerThreadCount());
    }

    @Deactivate
    public void deactivate() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    private static EventLoopGroup buildEventLoopGroup(final String name, final int threads) {
        return NettyTransportSupport.newEventLoopGroup(name, threads < 0 ? 0 : threads);
    }
}
