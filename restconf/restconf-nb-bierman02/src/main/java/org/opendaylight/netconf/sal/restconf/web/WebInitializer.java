/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.web;

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
import org.opendaylight.aaa.web.WebContextRegistration;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.netconf.sal.rest.impl.RestconfApplication;

/**
 * Initializes the bierman-02 endpoint.
 *
 * @author Thomas Pantelis
 */
@Singleton
public class WebInitializer {

    private final WebContextRegistration registration;

    @Inject
    public WebInitializer(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final RestconfApplication webApp,
            final CustomFilterAdapterConfiguration customFilterAdapterConfig) throws ServletException {

        WebContextBuilder webContextBuilder = WebContext.builder().contextPath("restconf").supportsSessions(false)
                .addServlet(ServletDetails.builder().servlet(servletSupport.createHttpServletBuilder(webApp).build())
                    .addUrlPattern("/*").build())

                // Allows user to add javax.servlet.Filter(s) in front of REST services
                .addFilter(FilterDetails.builder().filter(new CustomFilterAdapter(customFilterAdapterConfig))
                    .addUrlPattern("/*").build());

        webContextSecurer.requireAuthentication(webContextBuilder, "/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    @PreDestroy
    public void close() {
        if (registration != null) {
            registration.close();
        }
    }
}
