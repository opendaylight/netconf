/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.netconf.config.GlobalNetconfSshScheduledExecutor;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.osgi.NetconfMonitoringServiceImpl;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(factory = DefaultNetconfMonitoringService.FACTORY_NAME, service = NetconfMonitoringService.class)
public final class DefaultNetconfMonitoringService extends NetconfMonitoringServiceImpl {
    public static final String OSGI_TYPE = "netconf-server-monitoring";

    static final String FACTORY_NAME = "org.opendaylight.netconf.impl.mdsal.DefaultNetconfMonitoringService";

    private static final String OP_PROVIDER_PROP = ".opProvider";
    private static final String UPDATE_INTERVAL_PROP = ".updateInterval";

    @Activate
    public DefaultNetconfMonitoringService(
            @Reference(target = "(type=" + GlobalNetconfSshScheduledExecutor.OSGI_TYPE + ")")
            final ScheduledThreadPool threadPool,
            final Map<String, ?> properties) {
        super(OSGiNetconfServer.extractProp(properties, OP_PROVIDER_PROP, NetconfOperationServiceFactory.class),
            threadPool,
            OSGiNetconfServer.extractProp(properties, UPDATE_INTERVAL_PROP, Long.class));
    }

    @Override
    @Deactivate
    public void close() {
        super.close();
    }

    static Map<String, ?> props(final NetconfOperationServiceFactory opProvider, final long updateInterval) {
        return Map.of(
            "type", OSGI_TYPE,
            OP_PROVIDER_PROP, requireNonNull(opProvider),
            UPDATE_INTERVAL_PROP, updateInterval);
    }
}
