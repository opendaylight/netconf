/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.MappingServerRequest;

/**
 * A {@link ServerRequest} originating in {@link JaxRsRestconf}.
 *
 * @param <T> type of reported result
 */
abstract class JaxRsServerRequest<T> extends MappingServerRequest<T> {
    private final @NonNull AsyncResponse ar;

    @NonNullByDefault
    private JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final ErrorTagMapping errorTagMapping,
            final SecurityContext sc, final AsyncResponse ar, final QueryParameters queryParameters) {
        super(sc.getUserPrincipal(), queryParameters, defaultPrettyPrint, errorTagMapping);
        this.ar = requireNonNull(ar);
    }

    @NonNullByDefault
    JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final ErrorTagMapping errorTagMapping,
            final SecurityContext sc, final AsyncResponse ar) {
        this(defaultPrettyPrint, errorTagMapping, sc, ar, QueryParameters.of());
    }

    @NonNullByDefault
    JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint,final ErrorTagMapping errorTagMapping,
            final SecurityContext sc, final AsyncResponse ar, final UriInfo uriInfo) {
        this(defaultPrettyPrint, errorTagMapping, sc, ar, queryParamsOf(uriInfo));
    }

    @NonNullByDefault
    private static QueryParameters queryParamsOf(final UriInfo uriInfo) {
        try {
            return QueryParameters.ofMultiValue(uriInfo.getQueryParameters());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @Override
    public final TransportSession session() {
        // JAX-RS does not give us control over TCP sessions
        return null;
    }

    @Override
    protected final void onSuccess(final T result) {
        final Response response;
        try {
            response = transform(result);
        } catch (RequestException e) {
            failWith(e);
            return;
        }
        ar.resume(response);
    }

    @Override
    protected final void onFailure(final HttpStatusCode status, final FormattableBody body) {
        ar.resume(Response.status(status.code(), status.phrase())
            .entity(new JaxRsFormattableBody(body, prettyPrint()))
            .build());
    }

    @NonNullByDefault
    abstract Response transform(T result) throws RequestException;
}
