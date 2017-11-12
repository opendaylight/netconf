/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.services.simple.api.UpdateHandlers;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;

/**
 * An operation resource represents a protocol operation defined with the YANG
 * "rpc" statement. It is invoked using a POST method on the operation resource.
 *
 */
public interface RestconfInvokeOperationsService extends UpdateHandlers {

    /**
     * Invoke RPC operation.
     *
     * @param identifier
     *             module name and rpc identifier string for the desired
     *            operation
     * @param payload
     *             {@link NormalizedNodeContext} - the body of the operation
     * @param uriInfo
     *             URI info
     * @return {@link NormalizedNodeContext}
     */
    @POST
    @Path("/operations/{identifier:.+}")
    @Produces({ Rfc8040.MediaTypes.DATA + RestconfConstants.JSON, Rfc8040.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    @Consumes({ Rfc8040.MediaTypes.DATA + RestconfConstants.JSON, Rfc8040.MediaTypes.DATA, MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    NormalizedNodeContext invokeRpc(@Encoded @PathParam("identifier") String identifier,
            NormalizedNodeContext payload, @Context UriInfo uriInfo);
}
