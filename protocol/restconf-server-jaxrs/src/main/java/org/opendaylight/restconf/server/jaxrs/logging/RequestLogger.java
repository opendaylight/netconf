/*
 * Copyright © 2021 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs.logging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

/**
 * Request filter used for logging of all incoming HTTP requests.
 */
@Provider
@PreMatching
public final class RequestLogger implements ContainerRequestFilter {
    private final RestconfLoggingBroker loggingBroker;

    @Context
    private HttpServletRequest httpServletRequest;

    @Context
    private SecurityContext securityContext;

    /**
     * Creation of request logger.
     *
     * @param loggingBroker RESTCONF logging broker
     */
    public RequestLogger(final RestconfLoggingBroker loggingBroker) {
        this.loggingBroker = loggingBroker;
    }

    @Override
    public void filter(final ContainerRequestContext containerRequestContext) throws IOException {
        final InputStream in = containerRequestContext.getEntityStream();
        if (in.available() > 0) {
            final var out = new ByteArrayOutputStream();
            in.transferTo(out);
            final byte[] requestEntity = out.toByteArray();
            try {
                loggingBroker.logRequest(securityContext, containerRequestContext, httpServletRequest, requestEntity);
            } finally {
                containerRequestContext.setEntityStream(new ByteArrayInputStream(requestEntity));
            }
        } else {
            loggingBroker.logRequest(securityContext, containerRequestContext, httpServletRequest, null);
        }
    }
}