/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.web;

import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.DATA_SUBSCRIPTION;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.NOTIFICATION_STREAM;
import static org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants.BASE_URI_PATTERN;
import static org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants.NOTIF;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.filterchain.filters.CustomFilterAdapter;
import org.opendaylight.aaa.web.FilterDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.restconf.nb.rfc8040.DataStreamApplication;
import org.opendaylight.restconf.nb.rfc8040.RestconfApplication;
import org.opendaylight.restconf.nb.rfc8040.RootFoundApplication;
import org.opendaylight.restconf.nb.rfc8040.streams.WebSocketInitializer;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * Initializes the rfc8040 web app endpoint.
 *
 * @author Thomas Pantelis
 */
@Singleton
public final class WebInitializer implements AutoCloseable {
    private final Registration discoveryReg;
    private final Registration restconfReg;

    @Inject
    public WebInitializer(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final RestconfApplication webApp,
            final DataStreamApplication webAppNotif,
            final CustomFilterAdapterConfiguration customFilterAdapterConfig,
            final WebSocketInitializer webSocketServlet) throws ServletException {
        final var restconfBuilder = WebContext.builder()
            .contextPath("/" + BASE_URI_PATTERN)
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/*")
                .servlet(servletSupport.createHttpServletBuilder(webApp).build())
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/" + NOTIF + "/*")
                .servlet(servletSupport.createHttpServletBuilder(webAppNotif).build())
                .name("notificationServlet")
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/" + DATA_SUBSCRIPTION + "/*")
                .addUrlPattern("/" + NOTIFICATION_STREAM + "/*")
                .servlet(webSocketServlet)
                .build())

            // Allows user to add javax.servlet.Filter(s) in front of REST services
            .addFilter(FilterDetails.builder()
                .addUrlPattern("/*")
                .filter(new CustomFilterAdapter(customFilterAdapterConfig))
                .asyncSupported(true)
                .build());

        webContextSecurer.requireAuthentication(restconfBuilder, true, "/*");

        restconfReg = webServer.registerWebContext(restconfBuilder.build());

        final var discoveryBuilder = WebContext.builder()
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

    @PreDestroy
    @Override
    public void close() {
        discoveryReg.close();
        restconfReg.close();
    }
}
