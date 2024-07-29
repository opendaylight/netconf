/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static java.util.Objects.requireNonNull;

import java.security.Principal;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.MappingServerRequest;

/**
 * A {@link ServerRequest} originating in {@link JaxRsRestconf}.
 *
 * @param T type of reported result
 */
@NonNullByDefault
abstract class JaxRsServerRequest<T> extends MappingServerRequest<T> {
    private final SecurityContext sc;
    private final AsyncResponse ar;

    private JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final ErrorTagMapping errorTagMapping,
            final SecurityContext sc, final AsyncResponse ar, final QueryParameters queryParameters) {
        super(queryParameters, defaultPrettyPrint, errorTagMapping);
        this.sc = requireNonNull(sc);
        this.ar = requireNonNull(ar);
    }

    JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final ErrorTagMapping errorTagMapping,
            final SecurityContext sc, final AsyncResponse ar) {
        this(defaultPrettyPrint, errorTagMapping, sc, ar, QueryParameters.of());
    }

    JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint,final ErrorTagMapping errorTagMapping,
            final SecurityContext sc, final AsyncResponse ar, final UriInfo uriInfo) {
        this(defaultPrettyPrint, errorTagMapping, sc, ar, queryParamsOf(uriInfo));
    }

    private static QueryParameters queryParamsOf(final UriInfo uriInfo) {
        try {
            return QueryParameters.ofMultiValue(uriInfo.getQueryParameters());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @Override
    public final @Nullable Principal principal() {
        return sc.getUserPrincipal();
    }

    @Override
    protected final void onSuccess(final T result) {
        final Response response;
        try {
            response = transform(result);
        } catch (ServerException e) {
            completeWith(e);
            return;
        }
        ar.resume(response);
    }

    @Override
    protected final void onFailure(final HttpStatusCode status, final FormattableBody body) {
        ar.resume(Response.status(status.code(), status.phrase()).entity(body).build());
    }

    abstract Response transform(T result) throws ServerException;
}
