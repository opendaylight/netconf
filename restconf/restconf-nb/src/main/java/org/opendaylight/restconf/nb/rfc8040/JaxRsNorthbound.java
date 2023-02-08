/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.annotations.Beta;
import javax.servlet.ServletException;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory;
import org.opendaylight.controller.config.threadpool.util.ScheduledThreadPoolWrapper;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfDataStreamServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.restconf.nb.rfc8040.streams.WebSocketInitializer;
import org.opendaylight.restconf.nb.rfc8040.web.WebInitializer;

/**
 * Main entrypoint into RFC8040 northbound. Take care of wiring up all applications activating them through JAX-RS.
 */
@Beta
public final class JaxRsNorthbound implements AutoCloseable {
    private final WebInitializer webInitializer;

    public JaxRsNorthbound(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final CustomFilterAdapterConfiguration filterAdapterConfiguration,
            final DOMActionService actionService, final DOMDataBroker dataBroker,
            final DOMMountPointService mountPointService, final DOMNotificationService notificationService,
            final DOMRpcService rpcService, final DOMSchemaService schemaService,
            final DatabindProvider databindProvider,
            final String pingNamePrefix, final int pingMaxThreadCount, final int maximumFragmentLength,
            final int heartbeatInterval, final int idleTimeout, final boolean useSSE) throws ServletException {
        final var configuration = new Configuration(maximumFragmentLength, idleTimeout, heartbeatInterval, useSSE);
        final var scheduledThreadPool = new ScheduledThreadPoolWrapper(pingMaxThreadCount,
            new NamingThreadPoolFactory(pingNamePrefix));

        webInitializer = new WebInitializer(webServer, webContextSecurer, servletSupport,
            new RestconfApplication(databindProvider, mountPointService, dataBroker, rpcService, actionService,
                notificationService, schemaService, configuration),
            new DataStreamApplication(databindProvider, mountPointService,
                new RestconfDataStreamServiceImpl(scheduledThreadPool, configuration)),
            filterAdapterConfiguration,
            new WebSocketInitializer(scheduledThreadPool, configuration));
    }

    @Override
    public void close() {
        webInitializer.close();
    }
}
