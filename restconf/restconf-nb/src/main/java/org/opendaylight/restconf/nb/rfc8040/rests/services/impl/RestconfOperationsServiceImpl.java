/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Container that provides access to the data-model specific operations supported by the server.
 */
@Path("/")
public final class RestconfOperationsServiceImpl {
    private final DatabindProvider databindProvider;
    private final DOMMountPointService mountPointService;

    /**
     * Set {@link DatabindProvider} for getting actual {@link EffectiveModelContext}.
     *
     * @param databindProvider a {@link DatabindProvider}
     * @param mountPointService a {@link DOMMountPointService}
     */
    public RestconfOperationsServiceImpl(final DatabindProvider databindProvider,
            final DOMMountPointService mountPointService) {
        this.databindProvider = requireNonNull(databindProvider);
        this.mountPointService = requireNonNull(mountPointService);
    }

    /**
     * List RPC and action operations in RFC7951 format.
     *
     * @return A string containing a JSON document conforming to both RFC8040 and RFC7951.
     */
    @GET
    @Path("/operations")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
    public String getOperationsJSON() {
        return OperationsContent.JSON.bodyFor(databindProvider.currentContext().modelContext());
    }

    /**
     * Retrieve list of operations and actions supported by the server or device in JSON format.
     *
     * @param identifier path parameter to identify device and/or operation
     * @return A string containing a JSON document conforming to both RFC8040 and RFC7951.
     */
    @GET
    @Path("/operations/{identifier:.+}")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
    public String getOperationJSON(@PathParam("identifier") final String identifier) {
        return OperationsContent.JSON.bodyFor(ParserIdentifier.toInstanceIdentifier(identifier,
            databindProvider.currentContext().modelContext(), mountPointService));
    }

    /**
     * List RPC and action operations in RFC8040 XML format.
     *
     * @return A string containing an XML document conforming to both RFC8040 section 11.3.1 and page 84.
     */
    @GET
    @Path("/operations")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public String getOperationsXML() {
        return OperationsContent.XML.bodyFor(databindProvider.currentContext().modelContext());
    }

    /**
     * Retrieve list of operations and actions supported by the server or device in XML format.
     *
     * @param identifier path parameter to identify device and/or operation
     * @return A string containing an XML document conforming to both RFC8040 section 11.3.1 and page 84.
     */
    @GET
    @Path("/operations/{identifier:.+}")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public String getOperationXML(@PathParam("identifier") final String identifier) {
        return OperationsContent.XML.bodyFor(ParserIdentifier.toInstanceIdentifier(identifier,
            databindProvider.currentContext().modelContext(), mountPointService));
    }
}
