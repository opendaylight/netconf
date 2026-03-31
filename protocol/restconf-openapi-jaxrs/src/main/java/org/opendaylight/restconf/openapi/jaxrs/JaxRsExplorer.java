/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.jaxrs;

import java.io.IOException;
import java.net.URLConnection;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import org.opendaylight.restconf.openapi.impl.OpenApiStaticResources;

@Path("/")
public final class JaxRsExplorer {
    @GET
    public Response getRoot() throws IOException {
        return response("/", true);
    }

    @HEAD
    public Response headRoot() throws IOException {
        return response("/", false);
    }

    @GET
    @Path("{path: .*}")
    public Response getResource(@PathParam("path") final String path) throws IOException {
        return response(path, true);
    }

    @HEAD
    @Path("{path: .*}")
    public Response headResource(@PathParam("path") final String path) throws IOException {
        return response(path, false);
    }

    private static Response response(final String path, final boolean withContent) throws IOException {
        final var resource = OpenApiStaticResources.getResource(path);
        if (resource == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        final var mediaType = URLConnection.guessContentTypeFromName(resource.getFile());
        final var builder = Response.ok();
        if (mediaType != null) {
            builder.type(mediaType);
        }
        if (withContent) {
            builder.entity((StreamingOutput) output -> {
                try (var input = resource.openStream()) {
                    input.transferTo(output);
                }
            });
        }
        return builder.build();
    }
}
