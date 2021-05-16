/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.web;

import java.util.List;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.filterchain.filters.CustomFilterAdapter;
import org.opendaylight.aaa.web.FilterDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextBuilder;
import org.opendaylight.aaa.web.WebContextRegistration;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.restconf.nb.rfc8040.DataStreamApplication;
import org.opendaylight.restconf.nb.rfc8040.RestconfApplication;
import org.opendaylight.restconf.nb.rfc8040.RootFoundApplication;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.websockets.WebSocketInitializer;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;

/**
 * Initializes the rfc8040 web app endpoint.
 *
 * @author Thomas Pantelis
 */
@Singleton
public class WebInitializer {
    private final WebContextRegistration registration;

    @Inject
    public WebInitializer(@Reference final WebServer webServer, @Reference final WebContextSecurer webContextSecurer,
            @Reference final ServletSupport servletSupport, final RestconfApplication webApp,
            final DataStreamApplication webAppNotif,
            @Reference final CustomFilterAdapterConfiguration customFilterAdapterConfig,
            final WebSocketInitializer webSocketServlet) throws ServletException {
        WebContextBuilder webContextBuilder = WebContext.builder()
            .contextPath("/")
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern(RestconfConstants.BASE_URI_PATTERN + "/*")
                .servlet(servletSupport.createHttpServletBuilder(webApp).build())
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern(RestconfConstants.BASE_URI_PATTERN + "/notif/*")
                .servlet(servletSupport.createHttpServletBuilder(webAppNotif).build())
                .name("notificationServlet")
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addAllUrlPatterns(List.of(
                    RestconfConstants.BASE_URI_PATTERN + RestconfStreamsConstants.DATA_CHANGE_EVENT_STREAM_PATTERN,
                    RestconfConstants.BASE_URI_PATTERN + RestconfStreamsConstants.YANG_NOTIFICATION_STREAM_PATTERN))
                .servlet(webSocketServlet)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern(".well-known/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new RootFoundApplication(RestconfConstants.BASE_URI_PATTERN))
                    .build())
                .name("Rootfound")
                .build())

            // Allows user to add javax.servlet.Filter(s) in front of REST services
            .addFilter(FilterDetails.builder()
                .addUrlPattern("/*")
                .filter(new CustomFilterAdapter(customFilterAdapterConfig))
                .asyncSupported(true)
                .build());

        webContextSecurer.requireAuthentication(webContextBuilder, true, RestconfConstants.BASE_URI_PATTERN + "/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    @PreDestroy
    public void close() {
        if (registration != null) {
            registration.close();
        }
    }
}
