/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import static java.util.Objects.requireNonNull;

import io.netty.channel.EventLoopGroup;
import java.util.Map;
import org.opendaylight.netconf.server.NetconfServerFactoryImpl;
import org.opendaylight.netconf.server.ServerChannelInitializer;
import org.opendaylight.netconf.server.api.NetconfServerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component(factory = DefaultNetconfServerFactory.FACTORY_NAME, service = NetconfServerFactory.class)
public final class DefaultNetconfServerFactory extends NetconfServerFactoryImpl {
    static final String FACTORY_NAME = "org.opendaylight.netconf.northbound.DefaultNetconfServerFactory";

    private static final String BOSS_PROP = ".bossGroup";
    private static final String WORKER_PROP = ".workerGroup";
    private static final String INITIALIZER_PROP = ".initializer";

    @Activate
    public DefaultNetconfServerFactory(final Map<String, ?> properties) {
        super(OSGiNetconfServer.extractProp(properties, INITIALIZER_PROP, ServerChannelInitializer.class),
            OSGiNetconfServer.extractProp(properties, BOSS_PROP, EventLoopGroup.class),
            OSGiNetconfServer.extractProp(properties, WORKER_PROP, EventLoopGroup.class));
    }

    static Map<String, ?> props(final EventLoopGroup bossGroup, final EventLoopGroup workerGroup,
            final ServerChannelInitializer initializer) {
        return Map.of(
            "type", "netconf-server-factory",
            BOSS_PROP, requireNonNull(bossGroup),
            WORKER_PROP, requireNonNull(workerGroup),
            INITIALIZER_PROP, requireNonNull(initializer));
    }
}
