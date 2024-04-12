/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;

/**
 *
 */
@NonNullByDefault
final class JaxRsServerRequest<T> extends ServerRequest<T> {
    @FunctionalInterface
    interface Transform<T> {

        Response responseOf(PrettyPrintParam prettyPrint, T success) throws ServerException;
    }

    private final AsyncResponse ar;
    private final Transform<T> transform;

    JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final UriInfo uriInfo, final AsyncResponse ar,
            final Transform<T> transform) {
        super(QueryParameters.ofMultiValue(uriInfo.getQueryParameters()), defaultPrettyPrint);
        this.ar = requireNonNull(ar);
        this.transform = requireNonNull(transform);
    }

    @Override
    public void failWith(final ErrorTag errorTag, final FormattableBody body) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void succeedWith(final T success) {
        final Response response;
        try {
            response = transform.responseOf(prettyPrint(), success);
        } catch (ServerException e) {
            failWith(e);
            return;
        }
        ar.resume(response);
    }
}
