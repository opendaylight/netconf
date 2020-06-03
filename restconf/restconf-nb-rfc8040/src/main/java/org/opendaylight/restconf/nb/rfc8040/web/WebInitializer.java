/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.web;

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
import org.opendaylight.restconf.nb.rfc8040.RestconfApplication;
import org.opendaylight.restconf.nb.rfc8040.RestconfNotifApplication;
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
    public WebInitializer(@Reference WebServer webServer, @Reference WebContextSecurer webContextSecurer,
            @Reference ServletSupport servletSupport, RestconfApplication webApp,RestconfNotifApplication webAppNotif,
            @Reference CustomFilterAdapterConfiguration customFilterAdapterConfig) throws ServletException {
        WebContextBuilder webContextBuilder = WebContext.builder().contextPath(RestconfConstants.BASE_URI_PATTERN)
                .supportsSessions(false)
                .addServlet(ServletDetails.builder().servlet(servletSupport.createHttpServletBuilder(webApp).build())
                        .addUrlPattern("/*").build())
                .addServlet(ServletDetails.builder().servlet(servletSupport.createHttpServletBuilder(webAppNotif)
                        .build()).asyncSupported(true).addUrlPattern("/notif/*").name("notificationServlet").build())
                // Allows user to add javax.servlet.Filter(s) in front of REST services
                .addFilter(FilterDetails.builder().filter(new CustomFilterAdapter(customFilterAdapterConfig))
                    .addUrlPattern("/*").asyncSupported(true).build())
                .addFilter(FilterDetails.builder().filter(new org.eclipse.jetty.servlets.GzipFilter())
                    .putInitParam("mimeTypes",
                        "application/xml,application/yang.data+xml,xml,application/json,application/yang.data+json")
                    .addUrlPattern("/*").asyncSupported(true).build());

        webContextSecurer.requireAuthentication(webContextBuilder, true , "/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    @PreDestroy
    public void close() {
        if (registration != null) {
            registration.close();
        }
    }
}