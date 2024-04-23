/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.api;

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

/**
 * This service generates swagger (See <a
 * href="https://helloreverb.com/developers/swagger"
 * >https://helloreverb.com/developers/swagger</a>) compliant documentation for
 * RESTCONF APIs. The output of this is used by embedded Swagger UI.
 */
@Path("/")
public interface OpenApiService {

    /**
     * Generate OpenAPI specification document.
     *
     * @param uriInfo Requests {@link UriInfo}.
     * @param width Width is the number of child nodes processed for each module/node. This means that for example with
     *      width=3 we will process schema only for first 3 nodes in each module and each node that we process after.
     *      Value set to 0 or lesser means ignore width and to process all child nodes of a YANG module.
     * @return Response containing the OpenAPI document for all modules with number child nodes specified by
     *      {@code width}.
     * @throws IOException When I/O error occurs.
     */
    @GET
    @Path("/single")
    @Produces(MediaType.APPLICATION_JSON)
    Response getAllModulesDoc(@Context UriInfo uriInfo, @QueryParam("width") Integer width) throws IOException;

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @GET
    @Path("/{module}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getDocByModule(@PathParam("module") String module, @QueryParam("revision") String revision,
                            @Context UriInfo uriInfo, @QueryParam("width") Integer width) throws IOException;

    /**
     * Redirects to embedded swagger ui.
     */
    @GET
    @Path("/ui")
    @Produces(MediaType.TEXT_HTML)
    Response getApiExplorer(@Context UriInfo uriInfo);

    /**
     * Generates index document for Swagger UI. This document lists out all
     * modules with link to get APIs for each module. The API for each module is
     * served by <code> getDocByModule()</code> method.
     */
    @GET
    @Path("/mounts")
    @Produces(MediaType.APPLICATION_JSON)
    Response getListOfMounts(@Context UriInfo uriInfo);

    /**
     * Generate OpenAPI specification document listing APIs for module.
     *
     * @param uriInfo Requests {@link UriInfo}.
     * @param width Width is the number of child nodes processed for each module/node. This means that for example with
     *      width=3 we will process schema only for first 3 nodes in each module and each node that we process after.
     *      Value set to 0 or lesser means ignore width and to process all child nodes of a YANG module.
     * @return Response containing the OpenAPI document for all modules with number child nodes specified by
     *      {@code width}.
     * @throws IOException When I/O error occurs.
     */
    @GET
    @Path("/mounts/{instance}/{module}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getMountDocByModule(@PathParam("instance") String instanceNum,
                                 @PathParam("module") String module, @QueryParam("revision") String revision,
                                 @Context UriInfo uriInfo, @QueryParam("width") Integer width) throws IOException;

    /**
     * Generate OpenAPI specification document listing APIs for all modules of mount point.
     *
     * @param uriInfo Requests {@link UriInfo}.
     * @param width Width is the number of child nodes processed for each module/node. This means that for example with
     *      width=3 we will process schema only for first 3 nodes in each module and each node that we process after.
     *      Value set to 0 or lesser means ignore width and to process all child nodes of a YANG module.
     * @return Response containing the OpenAPI document for all modules with number child nodes specified by
     *      {@code width}.
     * @throws IOException When I/O error occurs.
     */
    @GET
    @Path("/mounts/{instance}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getMountDoc(@PathParam("instance") String instanceNum, @Context UriInfo uriInfo,
                         @QueryParam("width") Integer width) throws IOException;
}
