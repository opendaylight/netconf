/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.web;

import javax.ws.rs.core.Application;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.filterchain.filters.CustomFilterAdapter;
import org.opendaylight.aaa.web.FilterDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextBuilder;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.restconf.common.web.AbstractWebRegistrar;

/**
 * Initializes the rfc8040 web app endpoint.
 *
 * @author Thomas Pantelis
 */
public class Rfc8040WebRegistrarImpl extends AbstractWebRegistrar implements Rfc8040WebRegistrar {
    private final WebContextSecurer webContextSecurer;
    private final ServletSupport servletSupport;
    private final Application webApp;
    private final CustomFilterAdapterConfiguration customFilterAdapterConfig;

    public Rfc8040WebRegistrarImpl(WebServer webServer, WebContextSecurer webContextSecurer,
            ServletSupport servletSupport, Application webApp,
            CustomFilterAdapterConfiguration customFilterAdapterConfig) {
        super(webServer);
        this.webContextSecurer = webContextSecurer;
        this.servletSupport = servletSupport;
        this.webApp = webApp;
        this.customFilterAdapterConfig = customFilterAdapterConfig;
    }

    @Override
    protected WebContext createWebContext(boolean authenticate) {
        WebContextBuilder webContextBuilder = WebContext.builder().contextPath("rests").supportsSessions(true)
                .addServlet(ServletDetails.builder().servlet(servletSupport.createHttpServletBuilder(webApp).build())
                    .addUrlPattern("/*").build())

                // Allows user to add javax.servlet.Filter(s) in front of REST services
                .addFilter(FilterDetails.builder().filter(new CustomFilterAdapter(customFilterAdapterConfig))
                    .addUrlPattern("/*").build())

                .addFilter(FilterDetails.builder().filter(new org.eclipse.jetty.servlets.GzipFilter())
                    .putInitParam("mimeTypes",
                        "application/xml,application/yang.data+xml,xml,application/json,application/yang.data+json")
                    .addUrlPattern("/*").build());

        webContextSecurer.requireAuthentication(webContextBuilder, "/*");

        return webContextBuilder.build();
    }
}
