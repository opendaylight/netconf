/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.plugin.netconf.nb;

import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.netconf.impl.osgi.AggregatedNetconfOperationServiceFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * @author nite
 *
 */
@Component(service = {}, configurationPid = "org.opendaylight.netconf.impl")
@Designate(ocd = NetconfNorthboundPlugin.Configuration.class)
public final class NetconfNorthboundPlugin {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(description = "Connection timeout, in milliseconds", min = "1")
        int connectionTimeoutMillis() default 20000;
        @AttributeDefinition(description = "Monitoring update interval, in seconds", min = "1")
        int monitoringUpdateInterval() default 6;
    }

    private final AggregatedNetconfOperationServiceFactory serviceFactory =
        new AggregatedNetconfOperationServiceFactory();

    @Activate
    public NetconfNorthboundPlugin(@Reference final EventLoopGroup bossGroup,
            @Reference final EventLoopGroup workerGroup, @Reference final Timer timer,
            @Reference final ScheduledThreadPool threadPool) {



    }

    @Deactivate
    void deactivate() throws Exception {
        serviceFactory.close();

    }

}
