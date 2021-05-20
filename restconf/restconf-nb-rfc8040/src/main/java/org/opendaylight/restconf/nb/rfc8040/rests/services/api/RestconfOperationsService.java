/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;

/**
 * Container that provides access to the data-model specific operations supported by the server.
 */
public interface RestconfOperationsService {
    /**
     * List of rpc or action operations supported by the server.
     *
     * @param uriInfo URI information
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("/operations")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    NormalizedNodeContext getOperations(@Context UriInfo uriInfo);

    /**
     * Valid for mount points. List of operations supported by the server.
     *
     * @param identifier path parameter
     * @param uriInfo URI information
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("/operations/{identifier:.+}")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    NormalizedNodeContext getOperations(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);
}
