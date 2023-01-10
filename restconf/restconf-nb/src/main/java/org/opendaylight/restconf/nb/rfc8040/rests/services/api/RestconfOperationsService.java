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
import javax.ws.rs.core.MediaType;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;

/**
 * Container that provides access to the data-model specific operations supported by the server.
 */
public interface RestconfOperationsService {
    /**
     * List RPC and action operations in RFC7951 format.
     *
     * @return A string containing a JSON document conforming to both RFC8040 and RFC7951.
     */
    @GET
    @Path("/operations")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
    String getOperationsJSON();

    /**
     * Retrieve list of operations and action supported by the server or device.
     *
     * @param identifier path parameter
     * @return A string that contains JSON with available RPCs and actions.
     */
    @GET
    @Path("/operations/{identifier:.+}")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
    String getOperationJSON(@PathParam("identifier") String identifier);

    /**
     * List RPC and action operations in RFC8040 XML format.
     *
     * @return A string containing a JSON document conforming to both RFC8040 section 11.3.1 and page 84.
     */
    @GET
    @Path("/operations")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    String getOperationsXML();

    /**
     * Retrieve list of operations and action supported by the server or device.
     *
     * @param identifier path parameter
     * @return A string that contains XML with available RPCs and actions.
     */
    @GET
    @Path("/operations/{identifier:.+}")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    String getOperationXML(@PathParam("identifier") String identifier);
}
