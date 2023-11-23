/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
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
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonDataPostBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonPatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonResourceBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlDataPostBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlPatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlResourceBody;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.ErrorTags;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPostResult.CreateResource;
import org.opendaylight.restconf.server.api.DataPostResult.InvokeOperation;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OperationsGetResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.OperationOutput;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baseline RESTCONF implementation with JAX-RS. Interfaces to a {@link RestconfServer}.
 */
@Path("/")
public final class JaxRsRestconf {
    private static final Logger LOG = LoggerFactory.getLogger(JaxRsRestconf.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");

    private final RestconfServer server;

    public JaxRsRestconf(final RestconfServer server) {
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
    public void dataDELETE(@Encoded @PathParam("identifier") final JaxRsApiPath identifier,
            @Suspended final AsyncResponse ar) {
        server.dataDELETE(identifier.apiPath).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            protected Response transform(final Empty result) {
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
    public void dataGET(@Encoded @PathParam("identifier") final JaxRsApiPath identifier,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        final var readParams = QueryParams.newReadDataParams(uriInfo);
        completeDataGET(server.dataGET(identifier.apiPath, readParams), readParams, ar);
    }

    private static void completeDataGET(final RestconfFuture<NormalizedNodePayload> future,
            final ReadDataParams readParams, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            protected Response transform(final NormalizedNodePayload result) {
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
    public void dataXmlPATCH(@Encoded @PathParam("identifier") final JaxRsApiPath identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            completeDataPATCH(server.dataPATCH(identifier.apiPath, xmlBody), ar);
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
    public void dataJsonPATCH(@Encoded @PathParam("identifier") final JaxRsApiPath identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            completeDataPATCH(server.dataPATCH(identifier.apiPath, jsonBody), ar);
        }
    }

    private static void completeDataPATCH(final RestconfFuture<Empty> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            protected Response transform(final Empty result) {
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
    public void dataYangJsonPATCH(@Encoded @PathParam("identifier") final JaxRsApiPath identifier,
            final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonPatchBody(body)) {
            completeDataYangPATCH(server.dataPATCH(identifier.apiPath, jsonBody), ar);
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
    public void dataYangXmlPATCH(@Encoded @PathParam("identifier") final JaxRsApiPath identifier,
            final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            completeDataYangPATCH(server.dataPATCH(identifier.apiPath, xmlBody), ar);
        }
    }

    private static void completeDataYangPATCH(final RestconfFuture<PatchStatusContext> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            protected Response transform(final PatchStatusContext result) {
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
     * Create a top-level data resource.
     *
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void postDataJSON(final InputStream body, @Context final UriInfo uriInfo,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonChildBody(body)) {
            completeDataPOST(server.dataPOST(jsonBody, QueryParams.normalize(uriInfo)), uriInfo, ar);
        }
    }

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void postDataJSON(@Encoded @PathParam("identifier") final JaxRsApiPath identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        completeDataPOST(server.dataPOST(identifier.apiPath, new JsonDataPostBody(body),
            QueryParams.normalize(uriInfo)), uriInfo, ar);
    }

    /**
     * Create a top-level data resource.
     *
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void postDataXML(final InputStream body, @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlChildBody(body)) {
            completeDataPOST(server.dataPOST(xmlBody, QueryParams.normalize(uriInfo)), uriInfo, ar);
        }
    }

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void postDataXML(@Encoded @PathParam("identifier") final JaxRsApiPath identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        completeDataPOST(server.dataPOST(identifier.apiPath, new XmlDataPostBody(body), QueryParams.normalize(uriInfo)),
            uriInfo, ar);
    }

    private static void completeDataPOST(final RestconfFuture<? extends DataPostResult> future, final UriInfo uriInfo,
            final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<DataPostResult>(ar) {
            @Override
            protected Response transform(final DataPostResult result) {
                if (result instanceof CreateResource createResource) {
                    return Response.created(uriInfo.getBaseUriBuilder()
                            .path("data")
                            .path(createResource.createdPath())
                            .build())
                        .build();
                }
                if (result instanceof InvokeOperation invokeOperation) {
                    final var output = invokeOperation.output();
                    return output == null ? Response.status(Status.NO_CONTENT).build()
                        : Response.status(Status.OK).entity(output).build();
                }
                LOG.error("Unhandled result {}", result);
                return Response.serverError().build();
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
    public void dataJsonPUT(@Encoded @PathParam("identifier") final JaxRsApiPath identifier,
            @Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            completeDataPUT(server.dataPUT(identifier.apiPath, jsonBody, QueryParams.normalize(uriInfo)), ar);
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
    public void dataXmlPUT(@Encoded @PathParam("identifier") final JaxRsApiPath identifier,
            @Context final UriInfo uriInfo, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            completeDataPUT(server.dataPUT(identifier.apiPath, xmlBody, QueryParams.normalize(uriInfo)), ar);
        }
    }

    private static void completeDataPUT(final RestconfFuture<DataPutResult> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            protected Response transform(final DataPutResult result) {
                return switch (result) {
                    // Note: no Location header, as it matches the request path
                    case CREATED -> Response.status(Status.CREATED).build();
                    case REPLACED -> Response.noContent().build();
                };
            }
        });
    }

    /**
     * Get schema of specific module.
     *
     * @param identifier path parameter
     */
    @GET
    @Produces(YangConstants.RFC6020_YANG_MEDIA_TYPE)
    @Path("/modules/{identifier:.+}")
    public void modulesYangGET(@PathParam("identifier") final String identifier, @Suspended final AsyncResponse ar) {
        server.modulesYangGET(identifier).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            protected Response transform(final ModulesGetResult result) {
                return Response.ok(result.stream()).build();
            }
        });
    }

    /**
     * Get schema of specific module.
     *
     * @param identifier path parameter
     */
    @GET
    @Produces(YangConstants.RFC6020_YIN_MEDIA_TYPE)
    @Path("/modules/{identifier:.+}")
    public void modulesYinGET(@PathParam("identifier") final String identifier, @Suspended final AsyncResponse ar) {
        server.modulesYinGET(identifier).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            protected Response transform(final ModulesGetResult result) {
                return Response.ok(result.stream()).build();
            }
        });
    }

    /**
     * List RPC and action operations in RFC7951 format.
     *
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Path("/operations")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
    public void operationsJsonGET(@Suspended final AsyncResponse ar) {
        completeOperationsJsonGet(server.operationsGET(), ar);
    }

    /**
     * Retrieve list of operations and actions supported by the server or device in JSON format.
     *
     * @param operation path parameter to identify device and/or operation
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Path("/operations/{operation:.+}")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON })
    public void operationsJsonGET(@PathParam("operation") final JaxRsApiPath operation, final AsyncResponse ar) {
        completeOperationsGet(server.operationsGET(operation.apiPath), ar, OperationsGetResult::toJSON);
    }

    private static void completeOperationsJsonGet(final RestconfFuture<OperationsGetResult> future,
            final AsyncResponse ar) {
        completeOperationsGet(future, ar, OperationsGetResult::toJSON);
    }

    /**
     * List RPC and action operations in RFC8040 XML format.
     *
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Path("/operations")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public void operationsXmlGET(@Suspended final AsyncResponse ar) {
        completeOperationsXmlGet(server.operationsGET(), ar);
    }

    /**
     * Retrieve list of operations and actions supported by the server or device in XML format.
     *
     * @param operation path parameter to identify device and/or operation
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Path("/operations/{operation:.+}")
    @Produces({ MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public void operationsXmlGET(@PathParam("operation") final JaxRsApiPath operation, final AsyncResponse ar) {
        completeOperationsXmlGet(server.operationsGET(operation.apiPath), ar);
    }

    private static void completeOperationsXmlGet(final RestconfFuture<OperationsGetResult> future,
            final AsyncResponse ar) {
        completeOperationsGet(future, ar, OperationsGetResult::toXML);
    }

    private static void completeOperationsGet(final RestconfFuture<OperationsGetResult> future, final AsyncResponse ar,
            final Function<OperationsGetResult, String> toString) {
        future.addCallback(new JaxRsRestconfCallback<OperationsGetResult>(ar) {
            @Override
            protected Response transform(final OperationsGetResult result) {
                return Response.ok().entity(toString.apply(result)).build();
            }
        });
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
    public void operationsXmlPOST(@Encoded @PathParam("identifier") final JaxRsApiPath identifier,
            final InputStream body, @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlOperationInputBody(body)) {
            operationsPOST(identifier.apiPath, uriInfo, ar, xmlBody);
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
    public void operationsJsonPOST(@Encoded @PathParam("identifier") final JaxRsApiPath identifier,
            final InputStream body, @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonOperationInputBody(body)) {
            operationsPOST(identifier.apiPath, uriInfo, ar, jsonBody);
        }
    }

    private void operationsPOST(final ApiPath identifier, final UriInfo uriInfo, final AsyncResponse ar,
            final OperationInputBody body) {
        server.operationsPOST(uriInfo.getBaseUri(), identifier, body)
            .addCallback(new JaxRsRestconfCallback<OperationOutput>(ar) {
                @Override
                protected Response transform(final OperationOutput result) {
                    final var body = result.output();
                    return body == null ? Response.noContent().build()
                        : Response.ok().entity(new NormalizedNodePayload(result.operation(), body)).build();
                }
            });
    }

    /**
     * Get revision of IETF YANG Library module.
     *
     * @param ar {@link AsyncResponse} which needs to be completed
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
    public void yangLibraryVersionGET(@Suspended final AsyncResponse ar) {
        server.yangLibraryVersionGET().addCallback(new JaxRsRestconfCallback<NormalizedNodePayload>(ar) {
            @Override
            protected Response transform(final NormalizedNodePayload result) {
                return Response.ok().entity(result).build();
            }
        });
    }
}
