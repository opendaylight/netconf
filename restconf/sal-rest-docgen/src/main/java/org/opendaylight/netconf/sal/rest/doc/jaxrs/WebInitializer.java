/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.jaxrs;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import org.opendaylight.aaa.web.ResourceDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextBuilder;
import org.opendaylight.aaa.web.WebContextRegistration;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Initializes the wep app.
 *
 * @author Thomas Pantelis
 */
@Singleton
@Component(immediate = true)
public class WebInitializer {
    private final WebContextRegistration registration;

    @Inject
    @Activate
    public WebInitializer(final @Reference WebServer webServer, final @Reference WebContextSecurer webContextSecurer,
                          final @Reference ServletSupport servletSupport, final @Reference ApiDocApplication webApp)
            throws ServletException {
        WebContextBuilder webContextBuilder = WebContext.builder().contextPath("apidoc").supportsSessions(true)
            .addServlet(ServletDetails.builder().servlet(servletSupport.createHttpServletBuilder(webApp).build())
                    .addUrlPattern("/swagger2/apis/*").addUrlPattern("/swagger2/18/apis/*")
                    .addUrlPattern("/openapi3/apis/*").addUrlPattern("/openapi3/18/apis/*").build())
            .addResource(ResourceDetails.builder().name("/explorer").build())
            .addResource(ResourceDetails.builder().name("/18/explorer").build());

        webContextSecurer.requireAuthentication(webContextBuilder, "/swagger2/*", "/openapi3/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    @Deactivate
    @PreDestroy
    public void close() {
        registration.close();
    }
}
