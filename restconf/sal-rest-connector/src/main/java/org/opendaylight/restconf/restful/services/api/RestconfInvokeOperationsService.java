/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.utils.RestconfConstants;

/**
 * An operation resource represents a protocol operation defined with the YANG
 * "rpc" statement or a data-model specific action defined with a YANG "action"
 * statement. It is invoked using a POST method on the operation resource.
 *
 */
public interface RestconfInvokeOperationsService {

    /**
     * Invoke RPC operation
     *
     * @param identifier
     *            - module name and rpc identifier string for the desired
     *            operation
     * @param payload
     *            - {@link NormalizedNodeContext} - the body of the operation
     * @param uriInfo
     *            - URI info
     * @return {@link NormalizedNodeContext}
     */
    @POST
    @Path("/operations/{identifier:.+}")
    @Produces({ Draft11.MediaTypes.OPERATION + RestconfConstants.JSON,
            Draft11.MediaTypes.OPERATION + RestconfConstants.XML, Draft11.MediaTypes.DATA + RestconfConstants.JSON,
            Draft11.MediaTypes.DATA + RestconfConstants.XML, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML,
            MediaType.TEXT_XML })
    @Consumes({ Draft11.MediaTypes.OPERATION + RestconfConstants.JSON,
            Draft11.MediaTypes.OPERATION + RestconfConstants.XML, Draft11.MediaTypes.DATA + RestconfConstants.JSON,
            Draft11.MediaTypes.DATA + RestconfConstants.XML, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML,
            MediaType.TEXT_XML })
    public NormalizedNodeContext invokeRpc(@Encoded @PathParam("identifier") String identifier,
            NormalizedNodeContext payload, @Context UriInfo uriInfo);

    /**
     * Invoke ACTION operation
     *
     * @param identifier
     *            - the path to the data node where the action is defined
     * @param action
     *            - the name of the action
     * @param payload
     *            - {@link NormalizedNodeContext} - the body of the operation
     * @param uriInfo
     *            - URI info
     * @return {@link NormalizedNodeContext}
     */
    @POST
    @Path("/operations/{identifier:.+}/{action}")
    @Produces({ Draft11.MediaTypes.OPERATION + RestconfConstants.JSON,
            Draft11.MediaTypes.OPERATION + RestconfConstants.XML, Draft11.MediaTypes.DATA + RestconfConstants.JSON,
            Draft11.MediaTypes.DATA + RestconfConstants.XML, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML,
            MediaType.TEXT_XML })
    @Consumes({ Draft11.MediaTypes.OPERATION + RestconfConstants.JSON,
            Draft11.MediaTypes.OPERATION + RestconfConstants.XML, Draft11.MediaTypes.DATA + RestconfConstants.JSON,
            Draft11.MediaTypes.DATA + RestconfConstants.XML, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML,
            MediaType.TEXT_XML })
    public NormalizedNodeContext invokeAction(@Encoded @PathParam("identifier") String identifier,
            @Encoded @PathParam("action") String action, NormalizedNodeContext payload, @Context UriInfo uriInfo);
}
