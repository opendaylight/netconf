/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yanglib.impl;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.ws.rs.core.Application;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.yangtools.concepts.Registration;
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
@Component(service = { })
public final class WebInitializer implements AutoCloseable {
    private final Registration registration;

    @Activate
    @Inject
    public WebInitializer(@Reference final WebServer webServer, @Reference final WebContextSecurer webContextSecurer,
            @Reference final ServletSupport servletSupport, @Reference final Application webApp)
                throws ServletException {
        final var webContextBuilder = WebContext.builder()
            .name("RFC8525 YANG Library")
            .contextPath("/yanglib")
            .supportsSessions(true)
            .addServlet(ServletDetails.builder()
                .servlet(servletSupport.createHttpServletBuilder(webApp).build())
                .addUrlPattern("/*")
                .build());

        webContextSecurer.requireAuthentication(webContextBuilder, "/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        registration.close();
    }
}
