/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.jaxrs;

import java.io.IOException;
import java.net.URISyntaxException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Reference;

@Path("/")
public class JaxRsOpenApiService {
    private final OpenApiService openApiService;


    @Activate
    public JaxRsOpenApiService(@Reference OpenApiService openApiService) {
        this.openApiService = openApiService;
    }

    @GET
    @Path("/single")
    @Produces(MediaType.APPLICATION_JSON)
    Response getAllModulesDoc(@Context UriInfo uriInfo, @QueryParam("width") Integer width,
            @QueryParam("depth") Integer depth, @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit) throws IOException {
        final var resultStream = openApiService.getAllModulesDoc(uriInfo.getRequestUri(), width, depth, offset, limit);
        return Response.ok(resultStream).build();
    }

    @GET
    @Path("/single/meta")
    @Produces(MediaType.APPLICATION_JSON)
    Response getAllModulesMeta(@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit)
            throws IOException {
        final var resultMetadataStream = openApiService.getAllModulesMeta(offset, limit);
        return Response.ok(resultMetadataStream).build();
    }

    @GET
    @Path("/{module}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getDocByModule(@PathParam("module") String module, @QueryParam("revision") String revision,
            @Context UriInfo uriInfo, @QueryParam("width") Integer width, @QueryParam("depth") Integer depth)
            throws IOException {
        final var resultStream = openApiService.getDocByModule(module, revision, uriInfo.getRequestUri(), width, depth);
        return Response.ok(resultStream).build();
    }

    @GET
    @Path("/ui")
    @Produces(MediaType.TEXT_HTML)
    Response getApiExplorer(@Context UriInfo uriInfo) throws URISyntaxException {
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(openApiService.getApiExplorer().getPath()).build())
            .build();
    }

    @GET
    @Path("/mounts")
    @Produces(MediaType.APPLICATION_JSON)
    Response getListOfMounts(@Context UriInfo uriInfo) {
        final var entity = openApiService.getListOfMounts(uriInfo.getRequestUri());
        return Response.ok(entity).build();
    }

    @GET
    @Path("/mounts/{instance}/{module}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getMountDocByModule(@PathParam("instance") String instanceNum, @PathParam("module") String module,
            @QueryParam("revision") String revision, @Context UriInfo uriInfo, @QueryParam("width") Integer width,
            @QueryParam("depth") Integer depth) throws IOException {
        final var resultStream = openApiService.getMountDocByModule(instanceNum, module, revision,
            uriInfo.getRequestUri(), width, depth);
        return Response.ok(resultStream).build();
    }


    @GET
    @Path("/mounts/{instance}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getMountDoc(@PathParam("instance") String instanceNum, @Context UriInfo uriInfo,
            @QueryParam("width") Integer width, @QueryParam("depth") Integer depth,
            @QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) throws IOException {
        final var resultStream = openApiService.getMountDoc(instanceNum, uriInfo.getRequestUri(), width, depth, offset,
            limit);
        return Response.ok(resultStream).build();
    }

    @GET
    @Path("/mounts/{instance}/meta")
    @Produces(MediaType.APPLICATION_JSON)
    Response getMountMeta(@PathParam("instance") String instanceNum, @QueryParam("offset") Integer offset,
        @QueryParam("limit") Integer limit) throws IOException {
        final var resultMetaStream = openApiService.getMountMeta(instanceNum, offset, limit);
        return Response.ok(resultMetaStream).build();
    }
}
