/*
 * Copyright © 2021 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

/**
 * Response filter used for capturing and logging of RESTCONF responses that don't contain response body.
 */
@Provider
public final class ResponseWithoutBodyLogger implements ContainerResponseFilter {
    private final RestconfLoggingBroker loggingBroker;

    /**
     * Creation of response logger.
     *
     * @param loggingBroker RESTCONF logging broker
     */
    public ResponseWithoutBodyLogger(final RestconfLoggingBroker loggingBroker) {
        this.loggingBroker = loggingBroker;
    }

    @Override
    public void filter(final ContainerRequestContext requestContext,
                       final ContainerResponseContext responseContext) throws IOException {
        if (responseContext.getEntity() != null) {
            /* ContainerResponseFilter is used only for logging of responses without body, because we don't have access
               to serialized form of response entity */
            return;
        }
        loggingBroker.logResponseWithoutBody(requestContext, responseContext);
    }
}