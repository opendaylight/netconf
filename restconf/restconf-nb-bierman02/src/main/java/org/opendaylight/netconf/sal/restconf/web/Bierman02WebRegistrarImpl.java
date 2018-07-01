/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.web;

import java.util.concurrent.atomic.AtomicBoolean;
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
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Bierman02WebRegistrar.
 *
 * @author Thomas Pantelis
 */
public class Bierman02WebRegistrarImpl implements Bierman02WebRegistrar {
    private static final Logger LOG = LoggerFactory.getLogger(Bierman02WebRegistrarImpl.class);

    private final WebServer webServer;
    private final WebContextSecurer webContextSecurer;
    private final ServletSupport servletSupport;
    private final Application webApp;
    private final CustomFilterAdapterConfiguration customFilterAdapterConfig;
    private volatile WebContextRegistration registraton;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    public Bierman02WebRegistrarImpl(WebServer webServer,  WebContextSecurer webContextSecurer,
            ServletSupport servletSupport, Application webApp,
            CustomFilterAdapterConfiguration customFilterAdapterConfig) {
        this.webServer = webServer;
        this.webContextSecurer = webContextSecurer;
        this.servletSupport = servletSupport;
        this.webApp = webApp;
        this.customFilterAdapterConfig = customFilterAdapterConfig;
    }

    public void close() {
        if (registered.compareAndSet(true, false)) {
            if (registraton != null) {
                registraton.close();
            }
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
        if (!registered.compareAndSet(false, true)) {
            LOG.warn("Web context has already been registered", new Exception("call site"));
            return;
        }

        WebContextBuilder webContextBuilder = WebContext.builder().contextPath("restconf").supportsSessions(true)
                .addServlet(ServletDetails.builder().servlet(servletSupport.createHttpServletBuilder(webApp).build())
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
