/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security.auth.psswd.UsernamePasswordCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityFilter implements ContainerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityFilter.class);

    @Context
    private HttpServletRequest request;

    @Inject
    private AuthenticationService authenticationService;

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        final String authentication =
                requestContext.getHeaderString(HttpHeaders.AUTHORIZATION).substring("Basic ".length());
        final String[] values = new String(Base64.getDecoder().decode(authentication),
                Charset.forName("ASCII")).split(":");
        if (values.length < 2) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        final UsernamePasswordCredentials usernamePasswordCredentials =
                new UsernamePasswordCredentials(values[0], values[1]);

        if (!authenticationService.authenticate(usernamePasswordCredentials)) {
            LOG.info("User: {} not authenticated", values[0]);
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
    }
}
