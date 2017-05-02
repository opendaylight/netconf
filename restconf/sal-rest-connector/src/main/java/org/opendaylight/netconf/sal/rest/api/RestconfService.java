/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.rest.api.Draft02.MediaTypes;
import org.opendaylight.netconf.sal.rest.impl.Patch;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.PatchContext;
import org.opendaylight.netconf.sal.restconf.impl.PatchStatusContext;
import org.opendaylight.restconf.base.services.api.RestconfOperationsService;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.services.api.RestconfInvokeOperationsService;
import org.opendaylight.restconf.restful.services.api.RestconfStreamsSubscriptionService;

/**
 * The URI hierarchy for the RESTCONF resources consists of an entry point
 * container, 4 top-level resources, and 1 field.
 * <ul>
 * <li><b>/restconf</b> - {@link #getRoot()}
 * <ul>
 * <li><b>/config</b> - {@link #readConfigurationData(String, UriInfo)}
 * {@link #updateConfigurationData(String, NormalizedNodeContext, UriInfo)}
 * {@link #createConfigurationData(NormalizedNodeContext, UriInfo)}
 * {@link #createConfigurationData(String, NormalizedNodeContext, UriInfo)}
 * {@link #deleteConfigurationData(String)}
 * <li><b>/operational</b> - {@link #readOperationalData(String, UriInfo)}
 * <li>/modules - {@link #getModules(UriInfo)}
 * <ul>
 * <li>/module
 * </ul>
 * <li><b>/operations</b> -
 * {@link #invokeRpc(String, NormalizedNodeContext, UriInfo)}
 * {@link #invokeRpc(String, NormalizedNodeContext, UriInfo)}
 * <li>/version (field)
 * </ul>
 * </ul>
 */
@Path("/")
public interface RestconfService {

    String XML = "+xml";
    String JSON = "+json";

    @GET
    Object getRoot();

    /**
     * Get all modules supported by controller.
     *
     * @param uriInfo URI info
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/modules")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getModules(@Context UriInfo uriInfo);

    /**
     * Get all modules supported by mount point.
     *
     * @param identifier mount point identifier
     * @param uriInfo URI info
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/modules/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getModules(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);

    /**
     * Get module.
     *
     * @param identifier path to target
     * @param uriInfo URI info
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/modules/module/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getModule(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);

    /**
     * List of rpc or action operations supported by the server.
     *
     * @param uriInfo URI information
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfOperationsService#getOperations(UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/operations")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getOperations(@Context UriInfo uriInfo);

    /**
     * Valid for mount points. List of operations supported by the server.
     *
     * @param identifier path parameter
     * @param uriInfo URI information
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfOperationsService#getOperations(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/operations/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getOperations(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);

    /**
     * Invoke RPC operation.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param payload {@link NormalizedNodeContext} - the body of the operation
     * @param uriInfo URI info
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfInvokeOperationsService#invokeRpc(String, NormalizedNodeContext, UriInfo)}
     */
    @Deprecated
    @POST
    @Path("/operations/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.OPERATION + JSON, Draft02.MediaTypes.OPERATION + XML,
            Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    @Consumes({ Draft02.MediaTypes.OPERATION + JSON, Draft02.MediaTypes.OPERATION + XML,
            Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext invokeRpc(@Encoded @PathParam("identifier") String identifier, NormalizedNodeContext payload,
            @Context UriInfo uriInfo);

    /**
     * Invoke RPC with default empty payload.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param noPayload the body of the operation
     * @param uriInfo URI info
     * @return {@link NormalizedNodeContext}
     * @deprecated Method is not used and will be removed
     */
    @POST
    @Path("/operations/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.OPERATION + JSON, Draft02.MediaTypes.OPERATION + XML,
            Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    @Deprecated // method isn't use anywhere
    NormalizedNodeContext invokeRpc(@Encoded @PathParam("identifier") String identifier,
            @DefaultValue("") String noPayload, @Context UriInfo uriInfo);

    /**
     * Get target data resource from config data store.
     *
     * @param identifier path to target
     * @param uriInfo URI info
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/config/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext readConfigurationData(@Encoded @PathParam("identifier") String identifier,
            @Context UriInfo uriInfo);

    /**
     * Get target data resource from operational data store.
     *
     * @param identifier path to target
     * @param uriInfo URI info
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/operational/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext readOperationalData(@Encoded @PathParam("identifier") String identifier,
            @Context UriInfo uriInfo);

    /**
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param payload data node for put to config DS
     * @return {@link Response}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#putData(String, NormalizedNodeContext, UriInfo)}
     */
    @Deprecated
    @PUT
    @Path("/config/{identifier:.+}")
    @Consumes({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    Response updateConfigurationData(@Encoded @PathParam("identifier") String identifier,
            NormalizedNodeContext payload, @Context UriInfo uriInfo);

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param payload new data
     * @param uriInfo URI info
     * @return {@link Response}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#postData(String, NormalizedNodeContext, UriInfo)}
     */
    @Deprecated
    @POST
    @Path("/config/{identifier:.+}")
    @Consumes({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    Response createConfigurationData(@Encoded @PathParam("identifier") String identifier, NormalizedNodeContext payload,
            @Context UriInfo uriInfo);

    /**
     * Create a data resource.
     *
     * @param payload new data
     * @param uriInfo URI info
     * @return {@link Response}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#postData(NormalizedNodeContext, UriInfo)}
     */
    @Deprecated
    @POST
    @Path("/config")
    @Consumes({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    Response createConfigurationData(NormalizedNodeContext payload, @Context UriInfo uriInfo);

    /**
     * Delete the target data resource.
     *
     * @param identifier path to target
     * @return {@link Response}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#deleteData(String)}
     */
    @Deprecated
    @DELETE
    @Path("/config/{identifier:.+}")
    Response deleteConfigurationData(@Encoded @PathParam("identifier") String identifier);

    /**
     * Subscribe to stream.
     *
     * @param identifier stream identifier
     * @param uriInfo URI info
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *              {@link RestconfStreamsSubscriptionService#subscribeToStream(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/streams/stream/{identifier:.+}")
    NormalizedNodeContext subscribeToStream(@Encoded @PathParam("identifier") String identifier,
            @Context UriInfo uriInfo);

    /**
     * Get list of all streams.
     *
     * @param uriInfo URI info
     * @return {@link NormalizedNodeContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     **/
    @Deprecated
    @GET
    @Path("/streams")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext getAvailableStreams(@Context UriInfo uriInfo);

    /**
     * Ordered list of edits that are applied to the target datastore by the server.
     *
     * @param identifier path to target
     * @param context edits
     * @param uriInfo URI info
     * @return {@link PatchStatusContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#patchData(String, PatchContext, UriInfo)}
     */
    @Deprecated
    @Patch
    @Path("/config/{identifier:.+}")
    @Consumes({MediaTypes.PATCH + JSON, MediaTypes.PATCH + XML})
    @Produces({MediaTypes.PATCH_STATUS + JSON, MediaTypes.PATCH_STATUS + XML})
    PatchStatusContext patchConfigurationData(@Encoded @PathParam("identifier") String identifier, PatchContext
            context, @Context UriInfo uriInfo);

    /**
     * Ordered list of edits that are applied to the datastore by the server.
     *
     * @param context edits
     * @param uriInfo URI info
     * @return {@link PatchStatusContext}
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#patchData(PatchContext, UriInfo)}
     */
    @Deprecated
    @Patch
    @Path("/config")
    @Consumes({MediaTypes.PATCH + JSON, MediaTypes.PATCH + XML})
    @Produces({MediaTypes.PATCH_STATUS + JSON, MediaTypes.PATCH_STATUS + XML})
    PatchStatusContext patchConfigurationData(PatchContext context, @Context UriInfo uriInfo);
}
