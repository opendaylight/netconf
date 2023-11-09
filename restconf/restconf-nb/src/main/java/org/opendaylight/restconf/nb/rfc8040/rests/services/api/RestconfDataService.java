/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;

/**
 * The "{+restconf}/data" subtree represents the datastore resource type, which
 * is a collection of configuration data and state data nodes.
 *
 */
public interface RestconfDataService {
    /**
     * Get target data resource.
     *
     * @param identifier
     *            path to target
     * @param uriInfo
     *            URI info
     * @return {@link NormalizedNodePayload}
     */
    @GET
    @Path("/data/{identifier:.+}")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    Response readData(@Encoded @PathParam("identifier") String identifier, @Context UriInfo uriInfo);

    /**
     * Get target data resource from data root.
     *
     * @param uriInfo
     *            URI info
     * @return {@link NormalizedNodePayload}
     */
    @GET
    @Path("/data")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    Response readData(@Context UriInfo uriInfo);

    /**
     * Create or replace the target data resource.
     *
     * @param identifier
     *            path to target
     * @param payload
     *            data node for put to config DS
     * @return {@link Response}
     */
    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    Response putData(@Encoded @PathParam("identifier") String identifier, NormalizedNodePayload payload,
            @Context UriInfo uriInfo);

    /**
     * Create a data resource in target.
     *
     * @param identifier
     *            path to target
     * @param payload
     *            new data
     * @param uriInfo
     *            URI info
     * @return {@link Response}
     */
    @POST
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    Response postData(@Encoded @PathParam("identifier") String identifier, NormalizedNodePayload payload,
            @Context UriInfo uriInfo);

    /**
     * Create a data resource.
     *
     * @param payload
     *            new data
     * @param uriInfo
     *            URI info
     * @return {@link Response}
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    Response postData(NormalizedNodePayload payload, @Context UriInfo uriInfo);

    /**
     * Delete the target data resource.
     *
     * @param identifier
     *            path to target
     * @return {@link Response}
     */
    @DELETE
    @Path("/data/{identifier:.+}")
    Response deleteData(@Encoded @PathParam("identifier") String identifier);

    /**
     * Ordered list of edits that are applied to the target datastore by the
     * server.
     *
     * @param identifier
     *            path to target
     * @param context
     *            edits
     * @param uriInfo
     *            URI info
     * @return {@link Response}
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_PATCH_JSON,
        MediaTypes.APPLICATION_YANG_PATCH_XML
    })
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    Response patchData(@Encoded @PathParam("identifier") String identifier, PatchContext context,
                                 @Context UriInfo uriInfo);

    /**
     * Ordered list of edits that are applied to the datastore by the server.
     *
     * @param context
     *            edits
     * @param uriInfo
     *            URI info
     * @return {@link Response}
     */
    @PATCH
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_PATCH_JSON,
        MediaTypes.APPLICATION_YANG_PATCH_XML
    })
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    Response patchData(PatchContext context, @Context UriInfo uriInfo);


    /**
     * Partially modify the target data resource.
     *
     * @param identifier
     *            path to target
     * @param payload
     *            data node for put to config DS
     * @return {@link Response}
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    Response patchData(@Encoded @PathParam("identifier") String identifier, NormalizedNodePayload payload,
                       @Context UriInfo uriInfo);
}
