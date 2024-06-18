/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.AbstractServerRequest;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;

/**
 * A {@link ServerRequest} originating in {@link JaxRsRestconf}.
 *
 * @param T type of reported result
 */
@NonNullByDefault
abstract class JaxRsServerRequest<T> extends AbstractServerRequest<T> {
    private final AsyncResponse ar;

    private JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final AsyncResponse ar,
            final QueryParameters queryParameters) {
        super(queryParameters, defaultPrettyPrint);
        this.ar = requireNonNull(ar);
    }

    JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final AsyncResponse ar) {
        this(defaultPrettyPrint, ar, QueryParameters.of());
    }

    JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final AsyncResponse ar, final UriInfo uriInfo) {
        this(defaultPrettyPrint, ar, queryParamsOf(uriInfo));
    }

    private static QueryParameters queryParamsOf(final UriInfo uriInfo) {
        try {
            return QueryParameters.ofMultiValue(uriInfo.getQueryParameters());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @Override
    protected final void onSuccess(final T result) {
        final Response response;
        try {
            response = transform(result);
        } catch (ServerException e) {
            onFailure(e);
            return;
        }
        ar.resume(response);
    }

    @Override
    protected void onFailure(final ServerException ex) {
        ar.resume(ex);
    }

    abstract Response transform(T result) throws ServerException;
}
