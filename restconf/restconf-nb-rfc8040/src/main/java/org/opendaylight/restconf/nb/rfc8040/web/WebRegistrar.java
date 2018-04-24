/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.web;

import javax.servlet.ServletException;
import javax.ws.rs.core.Application;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.filterchain.filters.CustomFilterAdapter;
import org.opendaylight.aaa.web.FilterDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextBuilder;
import org.opendaylight.aaa.web.WebContextRegistration;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;

/**
 * Registers the web application.
 *
 * @author Thomas Pantelis
 */
public class WebRegistrar {
    private final WebContextRegistration registration;

    public WebRegistrar(WebServer webServer,  WebContextSecurer webContextSecurer,
            Application webApp, CustomFilterAdapterConfiguration customFilterAdapterConfig) throws ServletException {
        WebContextBuilder webContextBuilder = WebContext.builder().contextPath("rests").supportsSessions(true)
                .addServlet(ServletDetails.builder().servlet(
                        new com.sun.jersey.spi.container.servlet.ServletContainer(webApp))
                    .addUrlPattern("/*").build())

                // Allows user to add javax.servlet.Filter(s) in front of REST services
                .addFilter(FilterDetails.builder().filter(new CustomFilterAdapter(customFilterAdapterConfig))
                    .addUrlPattern("/*").build())

                .addFilter(FilterDetails.builder().filter(new org.eclipse.jetty.servlets.GzipFilter())
                    .putInitParam("mimeTypes",
                        "application/xml,application/yang.data+xml,xml,application/json,application/yang.data+json")
                    .addUrlPattern("/*").build());

        webContextSecurer.requireAuthentication(webContextBuilder, "/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    public void close() {
        if (registration != null) {
            registration.close();
        }
    }
}
