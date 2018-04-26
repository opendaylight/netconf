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
import org.opendaylight.aaa.web.WebContextBuilder;
import org.opendaylight.aaa.web.WebContextRegistration;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;

/**
 * Initializes the wep app.
 *
 * @author Thomas Pantelis
 */
public class WebInitializer {
    private final WebContextRegistration registration;

    public WebInitializer(WebServer webServer,  WebContextSecurer webContextSecurer, Application webApp)
            throws ServletException {
        WebContextBuilder webContextBuilder = WebContext.builder().contextPath("apidoc").supportsSessions(true)
            .addServlet(ServletDetails.builder().servlet(
                new com.sun.jersey.spi.container.servlet.ServletContainer(webApp))
                    .addUrlPattern("/apis/*").addUrlPattern("/18/apis/*").build())
            .addResource(ResourceDetails.builder().name("/explorer").build())
            .addResource(ResourceDetails.builder().name("/18/explorer").build());

        webContextSecurer.requireAuthentication(webContextBuilder, "/apis/*", "/18/apis/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    public void close() {
        registration.close();
    }
}
