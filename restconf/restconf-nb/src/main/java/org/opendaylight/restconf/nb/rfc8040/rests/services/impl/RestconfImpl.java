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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.server.api.OperationsContent;
import org.opendaylight.restconf.server.spi.OperationOutput;
import org.opendaylight.yangtools.yang.common.Revision;

/**
 * Baseline RESTCONF implementation with JAX-RS.
 */
@Path("/")
public final class RestconfImpl {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");

    private final MdsalRestconfServer server;

    public RestconfImpl(final MdsalRestconfServer server) {
        this.server = requireNonNull(server);
    }

    /**
     * Get target data resource from data root.
     *
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
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
    public void dataGET(@Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        final var readParams = QueryParams.newReadDataParams(uriInfo);
        completeDataGET(server.dataGET(readParams), readParams, ar);
    }

    /**
     * Get target data resource.
     *
     * @param identifier path to target
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
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
    public void dataGET(@Encoded @PathParam("identifier") final String identifier,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        final var readParams = QueryParams.newReadDataParams(uriInfo);
        completeDataGET(server.dataGET(identifier, readParams), readParams, ar);
    }

    private static void completeDataGET(final RestconfFuture<NormalizedNodePayload> future,
            final ReadDataParams readParams, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final NormalizedNodePayload result) {
                return switch (readParams.content()) {
                    case ALL, CONFIG -> {
                        final var type = result.data().name().getNodeType();
                        yield Response.status(Status.OK)
                            .entity(result)
                            // FIXME: is this ETag okay?
                            .header("ETag", '"' + type.getModule().getRevision().map(Revision::toString).orElse(null)
                                + "-" + type.getLocalName() + '"')
                            .header("Last-Modified", FORMATTER.format(LocalDateTime.now(Clock.systemUTC())))
                            .build();
                    }
                    case NONCONFIG -> Response.status(Status.OK).entity(result).build();
                };
            }
        });
    }

    /**
     * List RPC and action operations in RFC7951 format.
     *
     * @return A string containing a JSON document conforming to both RFC8040 and RFC7951.
     */
    @GET
    @Path("/operations")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
    public String operationsJsonGET() {
        return server.operationsGET().toJSON();
    }

    /**
     * Retrieve list of operations and actions supported by the server or device in JSON format.
     *
     * @param operation path parameter to identify device and/or operation
     * @return A string containing a JSON document conforming to both RFC8040 and RFC7951.
     */
    @GET
    @Path("/operations/{operation:.+}")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
    public String operationsJsonGET(@PathParam("operation") final String operation) {
        return operationsGET(operation).toJSON();
    }

    /**
     * List RPC and action operations in RFC8040 XML format.
     *
     * @return A string containing an XML document conforming to both RFC8040 section 11.3.1 and page 84.
     */
    @GET
    @Path("/operations")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public String operationsXmlGET() {
        return server.operationsGET().toXML();
    }

    /**
     * Retrieve list of operations and actions supported by the server or device in XML format.
     *
     * @param operation path parameter to identify device and/or operation
     * @return A string containing an XML document conforming to both RFC8040 section 11.3.1 and page 84.
     */
    @GET
    @Path("/operations/{operation:.+}")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public String operationsXmlGET(@PathParam("operation") final String operation) {
        return operationsGET(operation).toXML();
    }

    private @NonNull OperationsContent operationsGET(final String operation) {
        final var content = server.operationsGET(operation);
        if (content == null) {
            throw new NotFoundException();
        }
        return content;
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
    public void operationsXmlPOST(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlOperationInputBody(body)) {
            operationsPOST(identifier, uriInfo, ar, xmlBody);
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
    public void operationsJsonPOST(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonOperationInputBody(body)) {
            operationsPOST(identifier, uriInfo, ar, jsonBody);
        }
    }

    private void operationsPOST(final String identifier, final UriInfo uriInfo, final AsyncResponse ar,
            final OperationInputBody body) {
        server.operationsPOST(uriInfo.getBaseUri(), identifier, body)
            .addCallback(new JaxRsRestconfCallback<OperationOutput>(ar) {
                @Override
                Response transform(final OperationOutput result) {
                    final var body = result.output();
                    return body == null ? Response.noContent().build()
                        : Response.ok().entity(new NormalizedNodePayload(result.operation(), body)).build();
                }
            });
    }

    /**
     * Get revision of IETF YANG Library module.
     *
     * @return {@link NormalizedNodePayload}
     */
    @GET
    @Path("/yang-library-version")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public NormalizedNodePayload yangLibraryVersionGET() {
        return server.yangLibraryVersionGET();
    }
}
