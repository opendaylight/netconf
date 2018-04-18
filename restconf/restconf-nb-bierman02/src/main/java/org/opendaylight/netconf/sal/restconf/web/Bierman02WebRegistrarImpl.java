/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.web;

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
 * Implementation of Bierman02WebRegistrar.
 *
 * @author Thomas Pantelis
 */
public class Bierman02WebRegistrarImpl implements Bierman02WebRegistrar {

    private WebContextRegistration registraton;
    private final WebServer webServer;
    private final WebContextSecurer webContextSecurer;
    private final Application webApp;
    private final CustomFilterAdapterConfiguration customFilterAdapterConfig;

    public Bierman02WebRegistrarImpl(WebServer webServer,  WebContextSecurer webContextSecurer,
            Application webApp, CustomFilterAdapterConfiguration customFilterAdapterConfig) {
        this.webServer = webServer;
        this.webContextSecurer = webContextSecurer;
        this.webApp = webApp;
        this.customFilterAdapterConfig = customFilterAdapterConfig;
    }

    public void close() {
        if (registraton != null) {
            registraton.close();
        }
    }

    @Override
    public void registerWithAuthentication() {
        register(true);
    }

    @Override
    public void registerWithoutAuthentication() {
        register(false);
    }

    private void register(boolean authenticate) {
        WebContextBuilder webContextBuilder = WebContext.builder().contextPath("restconf").supportsSessions(true)
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

        if (authenticate) {
            webContextSecurer.requireAuthentication(webContextBuilder, "/*");
        }

        try {
            registraton = webServer.registerWebContext(webContextBuilder.build());
        } catch (ServletException e) {
            throw new RuntimeException("Failed to register the web context", e);
        }
    }
}
