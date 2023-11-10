/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.server.spi.OperationOutput;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Container that provides access to the data-model specific operations supported by the server.
 */
@Path("/")
public final class RestconfOperationsServiceImpl {
    private final MdsalRestconfServer server;

    /**
     * Set {@link DatabindProvider} for getting actual {@link EffectiveModelContext}.
     *
     * @param server a {@link MdsalRestconfServer}
     */
    public RestconfOperationsServiceImpl(final MdsalRestconfServer server) {
        this.server = requireNonNull(server);
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
        return OperationsContent.JSON.bodyFor(server.bindRequestRoot().inference());
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
        return OperationsContent.JSON.bodyFor(server.bindRequestPath(identifier).inference());
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
        return OperationsContent.XML.bodyFor(server.bindRequestRoot().inference());
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
        return OperationsContent.XML.bodyFor(server.bindRequestPath(identifier).inference());
    }


    /**
     * Invoke RPC operation.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param body the body of the operation
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link NormalizedNodePayload} output
     */
    @POST
    // FIXME: identifier is just a *single* QName
    @Path("/operations/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void invokeRpcXML(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlOperationInputBody(body)) {
            invokeRpc(identifier, uriInfo, ar, xmlBody);
        }
    }

    /**
     * Invoke RPC operation.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param body the body of the operation
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link NormalizedNodePayload} output
     */
    @POST
    // FIXME: identifier is just a *single* QName
    @Path("/operations/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void invokeRpcJSON(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonOperationInputBody(body)) {
            invokeRpc(identifier, uriInfo, ar, jsonBody);
        }
    }

    private void invokeRpc(final String identifier, final UriInfo uriInfo, final AsyncResponse ar,
            final OperationInputBody body) {
        server.invokeRpc(uriInfo.getBaseUri(), identifier, body)
            .addCallback(new JaxRsRestconfCallback<OperationOutput>(ar) {
                @Override
                Response transform(final OperationOutput result) {
                    final var body = result.output();
                    return body == null ? Response.noContent().build()
                        : Response.ok().entity(new NormalizedNodePayload(result.operation(), body)).build();
                }
            });
    }
}
