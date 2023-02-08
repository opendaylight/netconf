/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.DATA_SUBSCRIPTION;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.NOTIFICATION_STREAM;
import static org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants.BASE_URI_PATTERN;
import static org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants.NOTIF;

import com.google.common.annotations.Beta;
import javax.servlet.ServletException;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.filterchain.filters.CustomFilterAdapter;
import org.opendaylight.aaa.web.FilterDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
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
import org.opendaylight.yangtools.concepts.Registration;

/**
 * Main entrypoint into RFC8040 northbound. Take care of wiring up all applications activating them through JAX-RS.
 */
@Beta
public final class JaxRsNorthbound implements AutoCloseable {
    private final Registration discoveryReg;
    private final Registration restconfReg;

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

        final var restconfBuilder = WebContext.builder()
            .name("RFC8040 RESTCONF")
            .contextPath("/" + BASE_URI_PATTERN)
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new RestconfApplication(databindProvider, mountPointService, dataBroker, rpcService, actionService,
                        notificationService,schemaService, configuration)).build())
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/" + NOTIF + "/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new DataStreamApplication(databindProvider, mountPointService,
                        new RestconfDataStreamServiceImpl(scheduledThreadPool, configuration))).build())
                .name("notificationServlet")
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/" + DATA_SUBSCRIPTION + "/*")
                .addUrlPattern("/" + NOTIFICATION_STREAM + "/*")
                .servlet(new WebSocketInitializer(scheduledThreadPool, configuration))
                .build())

            // Allows user to add javax.servlet.Filter(s) in front of REST services
            .addFilter(FilterDetails.builder()
                .addUrlPattern("/*")
                .filter(new CustomFilterAdapter(filterAdapterConfiguration))
                .asyncSupported(true)
                .build());

        webContextSecurer.requireAuthentication(restconfBuilder, true, "/*");

        restconfReg = webServer.registerWebContext(restconfBuilder.build());

        final var discoveryBuilder = WebContext.builder()
            .name("RFC6415 Web Host Metadata")
            .contextPath("/.well-known")
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/*")
                .servlet(servletSupport.createHttpServletBuilder(new RootFoundApplication(BASE_URI_PATTERN)).build())
                .name("Rootfound")
                .build());

        webContextSecurer.requireAuthentication(discoveryBuilder, true, "/*");

        discoveryReg = webServer.registerWebContext(discoveryBuilder.build());
    }

    @Override
    public void close() {
        discoveryReg.close();
        restconfReg.close();
    }
}
