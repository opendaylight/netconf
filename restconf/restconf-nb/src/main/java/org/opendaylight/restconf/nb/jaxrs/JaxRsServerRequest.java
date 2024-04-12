/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import java.util.Date;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.AbstractServerRequest;
import org.opendaylight.restconf.nb.rfc8040.ErrorTagMapping;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.yangtools.yang.common.ErrorTag;

@NonNullByDefault
abstract class JaxRsServerRequest<T> extends AbstractServerRequest<T> {
    private final AsyncResponse ar;
    final UriInfo uriInfo;

    JaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final ErrorTagMapping errorTagMapping,
            final UriInfo uriInfo, final AsyncResponse ar) {
        super(QueryParameters.ofMultiValue(uriInfo.getQueryParameters()), defaultPrettyPrint, errorTagMapping);
        this.uriInfo = requireNonNull(uriInfo);
        this.ar = requireNonNull(ar);
    }

    @Override
    public final void failWith(final ErrorTag errorTag, final FormattableBody body) {
        final var status = statusOf(errorTag);
        ar.resume(Response.status(status.code(), status.phrase()).entity(body).build());
    }

    @Override
    public final void succeedWith(final T result) {
        final Response response;
        try {
            response = createResponse(prettyPrint(), result);
        } catch (ServerException e) {
            failWith(e);
            return;
        }
        ar.resume(response);
    }

    abstract Response createResponse(PrettyPrintParam prettyPrint, T result) throws ServerException;

    static final void fillConfigurationMetadata(final ResponseBuilder builder, final ConfigurationMetadata metadata) {
        final var etag = metadata.entityTag();
        if (etag != null) {
            builder.tag(new EntityTag(etag.value(), etag.weak()));
        }
        final var lastModified = metadata.lastModified();
        if (lastModified != null) {
            builder.lastModified(Date.from(lastModified));
        }
    }
}
