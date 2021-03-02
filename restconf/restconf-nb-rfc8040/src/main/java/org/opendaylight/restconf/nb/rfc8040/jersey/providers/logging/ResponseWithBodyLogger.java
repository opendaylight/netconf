/*
 * Copyright © 2021 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

/**
 * Response filter used for capturing and logging of RESTCONF responses that contains response body.
 */
@Provider
public final class ResponseWithBodyLogger implements WriterInterceptor {
    private final RestconfLoggingBroker loggingBroker;

    @Context
    private HttpServletResponse servletResponse;

    /**
     * Creation of response logger.
     *
     * @param loggingBroker RESTCONF logging broker
     */
    public ResponseWithBodyLogger(final RestconfLoggingBroker loggingBroker) {
        this.loggingBroker = loggingBroker;
    }

    @Override
    public void aroundWriteTo(final WriterInterceptorContext context) throws IOException, WebApplicationException {
        if (context.getEntity() == null) {
            // this should never happen
            context.proceed();
            return;
        }
        final OutputStream originalStream = context.getOutputStream();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        context.setOutputStream(baos);
        try {
            context.proceed();
            loggingBroker.logResponseWithBody(context, servletResponse, baos.toByteArray());
        } finally {
            baos.writeTo(originalStream);
            baos.close();
            context.setOutputStream(originalStream);
        }
    }
}