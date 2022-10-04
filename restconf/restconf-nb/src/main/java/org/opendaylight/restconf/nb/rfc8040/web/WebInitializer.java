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

import java.util.List;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.filterchain.filters.CustomFilterAdapter;
import org.opendaylight.aaa.web.FilterDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextBuilder;
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
public class WebInitializer {
    private final Registration registration;

    @Inject
    public WebInitializer(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final RestconfApplication webApp,
            final DataStreamApplication webAppNotif,
            final CustomFilterAdapterConfiguration customFilterAdapterConfig,
            final WebSocketInitializer webSocketServlet) throws ServletException {
        WebContextBuilder webContextBuilder = WebContext.builder()
            .contextPath("/")
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/" + BASE_URI_PATTERN + "/*")
                .servlet(servletSupport.createHttpServletBuilder(webApp).build())
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/" + BASE_URI_PATTERN + "/notif/*")
                .servlet(servletSupport.createHttpServletBuilder(webAppNotif).build())
                .name("notificationServlet")
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addAllUrlPatterns(List.of("/" + BASE_URI_PATTERN +  "/" + DATA_SUBSCRIPTION + "/*",
                        "/" + BASE_URI_PATTERN + "/" + NOTIFICATION_STREAM + "/*"))
                .servlet(webSocketServlet)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/.well-known/*")
                .servlet(servletSupport.createHttpServletBuilder(new RootFoundApplication(BASE_URI_PATTERN)).build())
                .name("Rootfound")
                .build())

            // Allows user to add javax.servlet.Filter(s) in front of REST services
            .addFilter(FilterDetails.builder()
                .addUrlPattern("/*")
                .filter(new CustomFilterAdapter(customFilterAdapterConfig))
                .asyncSupported(true)
                .build());

        webContextSecurer.requireAuthentication(webContextBuilder, true, "/" + BASE_URI_PATTERN + "/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    @PreDestroy
    public void close() {
        if (registration != null) {
            registration.close();
        }
    }
}
