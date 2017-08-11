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
import org.opendaylight.netconf.sal.rest.impl.PATCH;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.restconf.base.services.api.RestconfOperationsService;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.services.api.RestconfInvokeOperationsService;

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

    public static final String XML = "+xml";
    public static final String JSON = "+json";

    @GET
    public Object getRoot();

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/modules")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getModules(@Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/modules/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getModules(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/modules/module/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getModule(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfOperationsService#getOperations(UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/operations")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getOperations(@Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfOperationsService#getOperations(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/operations/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getOperations(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);

    /**
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
    public NormalizedNodeContext invokeRpc(@Encoded @PathParam("identifier") String identifier, NormalizedNodeContext payload,
            @Context UriInfo uriInfo);

    @POST
    @Path("/operations/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.OPERATION + JSON, Draft02.MediaTypes.OPERATION + XML,
            Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    @Deprecated // method isn't use anywhere
    public NormalizedNodeContext invokeRpc(@Encoded @PathParam("identifier") String identifier,
            @DefaultValue("") String noPayload, @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/config/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext readConfigurationData(@Encoded @PathParam("identifier") String identifier,
            @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/operational/{identifier:.+}")
    @Produces({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext readOperationalData(@Encoded @PathParam("identifier") String identifier,
            @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#putData(String, NormalizedNodeContext, UriInfo)}
     */
    @Deprecated
    @PUT
    @Path("/config/{identifier:.+}")
    @Consumes({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public Response updateConfigurationData(@Encoded @PathParam("identifier") String identifier,
            NormalizedNodeContext payload, @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#postData(String, NormalizedNodeContext, UriInfo)}
     */
    @Deprecated
    @POST
    @Path("/config/{identifier:.+}")
    @Consumes({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public Response createConfigurationData(@Encoded @PathParam("identifier") String identifier, NormalizedNodeContext payload,
            @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#postData(NormalizedNodeContext, UriInfo)}
     */
    @Deprecated
    @POST
    @Path("/config")
    @Consumes({ Draft02.MediaTypes.DATA + JSON, Draft02.MediaTypes.DATA + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public Response createConfigurationData(NormalizedNodeContext payload, @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#deleteData(String)}
     */
    @Deprecated
    @DELETE
    @Path("/config/{identifier:.+}")
    public Response deleteConfigurationData(@Encoded @PathParam("identifier") String identifier);

    @GET
    @Path("/streams/stream/{identifier:.+}")
    public NormalizedNodeContext subscribeToStream(@Encoded @PathParam("identifier") String identifier,
            @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#readData(String, UriInfo)}
     */
    @Deprecated
    @GET
    @Path("/streams")
    @Produces({ Draft02.MediaTypes.API + JSON, Draft02.MediaTypes.API + XML, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getAvailableStreams(@Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#patchData(String, PATCHContext, UriInfo)}
     */
    @Deprecated
    @PATCH
    @Path("/config/{identifier:.+}")
    @Consumes({MediaTypes.PATCH + JSON, MediaTypes.PATCH + XML})
    @Produces({MediaTypes.PATCH_STATUS + JSON, MediaTypes.PATCH_STATUS + XML})
    Response patchConfigurationData(@Encoded @PathParam("identifier") String identifier, PATCHContext
            context, @Context UriInfo uriInfo);

    /**
     * @deprecated do not use this method. It will be replaced by
     *             {@link RestconfDataService#patchData(PATCHContext, UriInfo)}
     */
    @Deprecated
    @PATCH
    @Path("/config")
    @Consumes({MediaTypes.PATCH + JSON, MediaTypes.PATCH + XML})
    @Produces({MediaTypes.PATCH_STATUS + JSON, MediaTypes.PATCH_STATUS + XML})
    Response patchConfigurationData(PATCHContext context, @Context UriInfo uriInfo);
}
