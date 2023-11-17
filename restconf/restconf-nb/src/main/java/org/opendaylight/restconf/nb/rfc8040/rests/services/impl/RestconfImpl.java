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
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
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
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonPatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonResourceBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlPatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlResourceBody;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.ErrorTags;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy.CreateOrReplaceResult;
import org.opendaylight.restconf.server.api.OperationsContent;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.OperationOutput;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.Revision;

/**
 * Baseline RESTCONF implementation with JAX-RS.
 */
@Path("/")
public final class RestconfImpl {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");

    private final RestconfServer server;

    public RestconfImpl(final RestconfServer server) {
        this.server = requireNonNull(server);
    }

    /**
     * Delete the target data resource.
     *
     * @param identifier path to target
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @DELETE
    @Path("/data/{identifier:.+}")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public void dataDELETE(@Encoded @PathParam("identifier") final String identifier,
            @Suspended final AsyncResponse ar) {
        server.dataDELETE(identifier).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final Empty result) {
                return Response.noContent().build();
            }
        });
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
     * Partially modify the target data store, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void dataXmlPATCH(final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            completeDataPATCH(server.dataPATCH(xmlBody), ar);
        }
    }

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void dataXmlPATCH(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            completeDataPATCH(server.dataPATCH(identifier, xmlBody), ar);
        }
    }

    /**
     * Partially modify the target data store, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void dataJsonPATCH(final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            completeDataPATCH(server.dataPATCH(jsonBody), ar);
        }
    }

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void dataJsonPATCH(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            completeDataPATCH(server.dataPATCH(identifier, jsonBody), ar);
        }
    }

    private static void completeDataPATCH(final RestconfFuture<Empty> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final Empty result) {
                return Response.ok().build();
            }
        });
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_JSON)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void dataYangJsonPATCH(final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonPatchBody(body)) {
            completeDataYangPATCH(server.dataPATCH(jsonBody), ar);
        }
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param body YANG Patch body
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_JSON)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void dataYangJsonPATCH(@Encoded @PathParam("identifier") final String identifier,
            final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonPatchBody(body)) {
            completeDataYangPATCH(server.dataPATCH(identifier, jsonBody), ar);
        }
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_XML)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void dataYangXmlPATCH(final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            completeDataYangPATCH(server.dataPATCH(xmlBody), ar);
        }
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param body YANG Patch body
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_XML)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void dataYangXmlPATCH(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            completeDataYangPATCH(server.dataPATCH(identifier, xmlBody), ar);
        }
    }

    private static void completeDataYangPATCH(final RestconfFuture<PatchStatusContext> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final PatchStatusContext result) {
                return Response.status(statusOf(result)).entity(result).build();
            }

            private static Status statusOf(final PatchStatusContext result) {
                if (result.ok()) {
                    return Status.OK;
                }
                final var globalErrors = result.globalErrors();
                if (globalErrors != null && !globalErrors.isEmpty()) {
                    return statusOfFirst(globalErrors);
                }
                for (var edit : result.editCollection()) {
                    if (!edit.isOk()) {
                        final var editErrors = edit.getEditErrors();
                        if (editErrors != null && !editErrors.isEmpty()) {
                            return statusOfFirst(editErrors);
                        }
                    }
                }
                return Status.INTERNAL_SERVER_ERROR;
            }

            private static Status statusOfFirst(final List<RestconfError> error) {
                return ErrorTags.statusOf(error.get(0).getErrorTag());
            }
        });
    }

    /**
     * Replace the data store.
     *
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void dataJsonPUT(@Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            completeDataPUT(server.dataPUT(jsonBody, QueryParams.normalize(uriInfo)), ar);
        }
    }

    /**
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void dataJsonPUT(@Encoded @PathParam("identifier") final String identifier,
            @Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            completeDataPUT(server.dataPUT(identifier, jsonBody, QueryParams.normalize(uriInfo)), ar);
        }
    }

    /**
     * Replace the data store.
     *
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void dataXmlPUT(@Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            completeDataPUT(server.dataPUT(xmlBody, QueryParams.normalize(uriInfo)), ar);
        }
    }

    /**
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void dataXmlPUT(@Encoded @PathParam("identifier") final String identifier,
            @Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            completeDataPUT(server.dataPUT(identifier, xmlBody, QueryParams.normalize(uriInfo)), ar);
        }
    }

    private static void completeDataPUT(final RestconfFuture<CreateOrReplaceResult> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final CreateOrReplaceResult result) {
                return switch (result) {
                    // Note: no Location header, as it matches the request path
                    case CREATED -> Response.status(Status.CREATED).build();
                    case REPLACED -> Response.noContent().build();
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
