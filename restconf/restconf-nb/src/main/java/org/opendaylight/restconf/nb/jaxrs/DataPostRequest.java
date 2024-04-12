/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.ErrorTagMapping;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class DataPostRequest extends JaxRsServerRequest<DataPostResult> {
    private static final Logger LOG = LoggerFactory.getLogger(DataPostRequest.class);

    DataPostRequest(final PrettyPrintParam defaultPrettyPrint, final ErrorTagMapping errorTagMapping,
            final UriInfo uriInfo, final AsyncResponse ar) {
        super(defaultPrettyPrint, errorTagMapping, uriInfo, ar);
    }

    @Override
    Response createResponse(final PrettyPrintParam prettyPrint, final DataPostResult result) throws ServerException {
        if (result instanceof CreateResourceResult createResource) {
            final var builder = Response.created(uriInfo.getBaseUriBuilder()
                .path("data")
                .path(createResource.createdPath().toString())
                .build());
            fillConfigurationMetadata(builder, createResource);
            return builder.build();
        }
        if (result instanceof InvokeResult invokeOperation) {
            final var output = invokeOperation.output();
            return output == null ? Response.noContent().build()
                : Response.ok().entity(new JaxRsFormattableBody(output, prettyPrint)).build();
        }
        LOG.error("Unhandled result {}", result);
        return Response.serverError().build();
    }

}
