/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639;

import javax.servlet.ServletException;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextBuilder;
import org.opendaylight.aaa.web.WebContextRegistration;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;

public class RFC8639WebInitializer {

    private final WebContextRegistration registration;

    public RFC8639WebInitializer(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final RFC8639RestconfApp webApp) throws ServletException {
        final WebContextBuilder webContextBuilder =  WebContext.builder()
                .contextPath("notifications")
                .supportsSessions(false)
                .addServlet(ServletDetails.builder().servlet(servletSupport.createHttpServletBuilder(webApp).build())
                        .addUrlPattern("/*")
                        .build());

        webContextSecurer.requireAuthentication(webContextBuilder, "/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    public void close() {
        if (registration != null) {
            registration.close();
        }
    }
}