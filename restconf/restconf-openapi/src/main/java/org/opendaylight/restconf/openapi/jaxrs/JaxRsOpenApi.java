/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.jaxrs;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.openapi.api.OpenApiService;

@Provider
@Path("/")
public final class JaxRsOpenApi {
    private final OpenApiService openApiService;

    public JaxRsOpenApi(final OpenApiService openApiService) {
        this.openApiService = requireNonNull(openApiService);
    }

    @GET
    @Path("/single")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllModulesDoc(@Context UriInfo uriInfo, @QueryParam("width") Integer width,
            @QueryParam("depth") Integer depth, @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit) throws IOException {
        final var resultStream = openApiService.getAllModulesDoc(uriInfo.getRequestUri(), width, depth, offset, limit);
        return Response.ok(resultStream).build();
    }

    @GET
    @Path("/single/meta")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllModulesMeta(@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit)
            throws IOException {
        final var resultMetadataStream = openApiService.getAllModulesMeta(offset, limit);
        return Response.ok(resultMetadataStream).build();
    }

    @GET
    @Path("/{module}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDocByModule(@PathParam("module") String module, @QueryParam("revision") String revision,
            @Context UriInfo uriInfo, @QueryParam("width") Integer width, @QueryParam("depth") Integer depth)
            throws IOException {
        final var resultStream = openApiService.getDocByModule(module, revision, uriInfo.getRequestUri(), width, depth);
        return Response.ok(resultStream).build();
    }

    @GET
    @Path("/ui")
    @Produces(MediaType.TEXT_HTML)
    public Response getApiExplorer(@Context UriInfo uriInfo) {
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("../../explorer/index.html").build()).build();
    }

    @GET
    @Path("/mounts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getListOfMounts(@Context UriInfo uriInfo) {
        final var entity = openApiService.getListOfMounts();
        return Response.ok(entity).build();
    }

    @GET
    @Path("/mounts/{instance}/{module}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMountDocByModule(@PathParam("instance") String instanceNum, @PathParam("module") String module,
            @QueryParam("revision") String revision, @Context UriInfo uriInfo, @QueryParam("width") Integer width,
            @QueryParam("depth") Integer depth) throws IOException {
        final var resultStream = openApiService.getMountDocByModule(instanceNum, module, revision,
            uriInfo.getRequestUri(), width, depth);
        return Response.ok(resultStream).build();
    }


    @GET
    @Path("/mounts/{instance}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMountDoc(@PathParam("instance") String instanceNum, @Context UriInfo uriInfo,
            @QueryParam("width") Integer width, @QueryParam("depth") Integer depth,
            @QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) throws IOException {
        final var resultStream = openApiService.getMountDoc(instanceNum, uriInfo.getRequestUri(), width, depth, offset,
            limit);
        return Response.ok(resultStream).build();
    }

    @GET
    @Path("/mounts/{instance}/meta")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMountMeta(@PathParam("instance") String instanceNum, @QueryParam("offset") Integer offset,
        @QueryParam("limit") Integer limit) throws IOException {
        final var resultMetaStream = openApiService.getMountMeta(instanceNum, offset, limit);
        return Response.ok(resultMetaStream).build();
    }
}
