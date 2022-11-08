/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.jaxrs;

import javax.servlet.ServletException;
import javax.ws.rs.core.Application;
import org.opendaylight.aaa.web.ResourceDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * Initializes the wep app.
 *
 * @author Thomas Pantelis
 */
public final class WebInitializer implements AutoCloseable {
    private final Registration registration;

    public WebInitializer(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final Application webApp) throws ServletException {
        var webContextBuilder = WebContext.builder()
            .contextPath("/apidoc")
            .supportsSessions(true)
            .addServlet(ServletDetails.builder()
                .servlet(servletSupport.createHttpServletBuilder(webApp).build())
                .addUrlPattern("/swagger2/apis/*")
                .addUrlPattern("/openapi3/apis/*")
                .build())
            .addResource(ResourceDetails.builder().name("/explorer").build());

        webContextSecurer.requireAuthentication(webContextBuilder, "/swagger2/*", "/openapi3/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    @Override
    public void close() {
        registration.close();
    }
}
