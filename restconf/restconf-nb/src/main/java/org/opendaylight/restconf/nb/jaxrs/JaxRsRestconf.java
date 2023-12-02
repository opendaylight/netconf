/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import javax.inject.Singleton;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
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
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPostResult.CreateResource;
import org.opendaylight.restconf.server.api.DataPostResult.InvokeOperation;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OperationsGetResult;
import org.opendaylight.restconf.server.api.OperationsPostResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baseline RESTCONF implementation with JAX-RS. Interfaces to a {@link RestconfServer}. Since we need {@link ApiPath}
 * arguments, we also implement {@link ParamConverterProvider} and provide the appropriate converter. This has the nice
 * side-effect of suppressing <a href="https://github.com/eclipse-ee4j/jersey/issues/3700">Jersey warnings</a>.
 */
@Path("/")
@Singleton
public final class JaxRsRestconf implements ParamConverterProvider {
    private static final Logger LOG = LoggerFactory.getLogger(JaxRsRestconf.class);
    private static final CacheControl NO_CACHE = CacheControl.valueOf("no-cache");
    private static final ParamConverter<ApiPath> API_PATH_CONVERTER = new ParamConverter<>() {
        @Override
        public ApiPath fromString(final String value) {
            final var str = nonnull(value);
            try {
                return ApiPath.parseUrl(str);
            } catch (ParseException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }

        @Override
        public String toString(final ApiPath value) {
            return nonnull(value).toString();
        }

        private static <T> @NonNull T nonnull(final @Nullable T value) {
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            return value;
        }
    };

    private final RestconfServer server;

    public JaxRsRestconf(final RestconfServer server) {
        this.server = requireNonNull(server);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(final Class<T> rawType, final Type genericType,
            final Annotation[] annotations) {
        return ApiPath.class.equals(rawType) ? (ParamConverter<T>) API_PATH_CONVERTER : null;
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
    public void dataDELETE(@Encoded @PathParam("identifier") final ApiPath identifier,
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
        completeDataGET(server.dataGET(QueryParams.newDataGetParams(uriInfo)), ar);
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
    public void dataGET(@Encoded @PathParam("identifier") final ApiPath identifier, @Context final UriInfo uriInfo,
            @Suspended final AsyncResponse ar) {
        completeDataGET(server.dataGET(identifier, QueryParams.newDataGetParams(uriInfo)), ar);
    }

    private static void completeDataGET(final RestconfFuture<DataGetResult> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final DataGetResult result) {
                final var builder = Response.status(Status.OK)
                    .entity(result.payload())
                    .cacheControl(NO_CACHE);
                fillConfigurationMetadata(builder, result);
                return builder.build();
            }
        });
    }

    private static void fillConfigurationMetadata(final ResponseBuilder builder, final ConfigurationMetadata metadata) {
        final var etag = metadata.entityTag();
        if (etag != null) {
            builder.tag(new EntityTag(etag.value(), etag.weak()));
        }
        final var lastModified = metadata.lastModified();
        if (lastModified != null) {
            builder.lastModified(Date.from(lastModified));
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
    public void dataXmlPATCH(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
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
    public void dataJsonPATCH(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            completeDataPATCH(server.dataPATCH(identifier, jsonBody), ar);
        }
    }

    private static void completeDataPATCH(final RestconfFuture<DataPatchResult> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final DataPatchResult result) {
                final var builder = Response.ok();
                fillConfigurationMetadata(builder, result);
                return builder.build();
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
    public void dataYangJsonPATCH(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
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
    public void dataYangXmlPATCH(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            completeDataYangPATCH(server.dataPATCH(identifier, xmlBody), ar);
        }
    }

    private static void completeDataYangPATCH(final RestconfFuture<DataYangPatchResult> future,
            final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final DataYangPatchResult result) {
                final var status = result.status();
                final var builder = Response.status(statusOf(status)).entity(status);
                fillConfigurationMetadata(builder, result);
                return builder.build();
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
    public void postDataJSON(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        completeDataPOST(server.dataPOST(identifier, new JsonDataPostBody(body), QueryParams.normalize(uriInfo)),
            uriInfo, ar);
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
    public void postDataXML(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        completeDataPOST(server.dataPOST(identifier, new XmlDataPostBody(body), QueryParams.normalize(uriInfo)),
            uriInfo, ar);
    }

    private static void completeDataPOST(final RestconfFuture<? extends DataPostResult> future, final UriInfo uriInfo,
            final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<DataPostResult>(ar) {
            @Override
            Response transform(final DataPostResult result) {
                if (result instanceof CreateResource createResource) {
                    final var builder = Response.created(uriInfo.getBaseUriBuilder()
                        .path("data")
                        .path(createResource.createdPath())
                        .build());
                    fillConfigurationMetadata(builder, createResource);
                    return builder.build();
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
    public void dataJsonPUT(@Encoded @PathParam("identifier") final ApiPath identifier, @Context final UriInfo uriInfo,
            final InputStream body, @Suspended final AsyncResponse ar) {
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
    public void dataXmlPUT(@Encoded @PathParam("identifier") final ApiPath identifier, @Context final UriInfo uriInfo,
            final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            completeDataPUT(server.dataPUT(identifier, xmlBody, QueryParams.normalize(uriInfo)), ar);
        }
    }

    private static void completeDataPUT(final RestconfFuture<DataPutResult> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final DataPutResult result) {
                // Note: no Location header, as it matches the request path
                final var builder = result.created() ? Response.status(Status.CREATED) : Response.noContent();
                fillConfigurationMetadata(builder, result);
                return builder.build();
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
    public void operationsJsonGET(@PathParam("operation") final ApiPath operation, @Suspended final AsyncResponse ar) {
        completeOperationsGet(server.operationsGET(operation), ar, OperationsGetResult::toJSON);
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
    public void operationsXmlGET(@PathParam("operation") final ApiPath operation, @Suspended final AsyncResponse ar) {
        completeOperationsXmlGet(server.operationsGET(operation), ar);
    }

    private static void completeOperationsXmlGet(final RestconfFuture<OperationsGetResult> future,
            final AsyncResponse ar) {
        completeOperationsGet(future, ar, OperationsGetResult::toXML);
    }

    private static void completeOperationsGet(final RestconfFuture<OperationsGetResult> future, final AsyncResponse ar,
            final Function<OperationsGetResult, String> toString) {
        future.addCallback(new JaxRsRestconfCallback<OperationsGetResult>(ar) {
            @Override
            Response transform(final OperationsGetResult result) {
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
    public void operationsXmlPOST(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
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
    public void operationsJsonPOST(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonOperationInputBody(body)) {
            operationsPOST(identifier, uriInfo, ar, jsonBody);
        }
    }

    private void operationsPOST(final ApiPath identifier, final UriInfo uriInfo, final AsyncResponse ar,
            final OperationInputBody body) {
        server.operationsPOST(uriInfo.getBaseUri(), identifier, body)
            .addCallback(new JaxRsRestconfCallback<OperationsPostResult>(ar) {
                @Override
                Response transform(final OperationsPostResult result) {
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
            Response transform(final NormalizedNodePayload result) {
                return Response.ok().entity(result).build();
            }
        });
    }

    // FIXME: References to these resources are generated by our yang-library implementation. That means:
    //        - We really need to formalize the parameter structure so we get some help from JAX-RS during matching
    //          of three things:
    //          - optional yang-ext:mount prefix(es)
    //          - mandatory module name
    //          - optional module revision
    //        - We really should use /yang-library-module/{name}(/{revision})?
    //        - We seem to be lacking explicit support for submodules in there -- and those locations should then point
    //          to /yang-library-submodule/{moduleName}(/{moduleRevision})?/{name}(/{revision})? so as to look the
    //          submodule up efficiently and allow for the weird case where there are two submodules with the same name
    //          (that is currently not supported by the parser, but it will be in the future)
    //        - It does not make sense to support yang-ext:mount, unless we also intercept mount points and rewrite
    //          yang-library locations. We most likely want to do that to ensure users are not tempted to connect to
    //          wild destinations

    /**
     * Get schema of specific module.
     *
     * @param fileName source file name
     * @param revision source revision
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Produces(YangConstants.RFC6020_YANG_MEDIA_TYPE)
    @Path("/" + URLConstants.MODULES_SUBPATH + "/{fileName : [^/]+}")
    public void modulesYangGET(@PathParam("fileName") final String fileName,
            @QueryParam("revision") final String revision, @Suspended final AsyncResponse ar) {
        completeModulesGET(server.modulesYangGET(fileName, revision), ar);
    }

    /**
     * Get schema of specific module.
     *
     * @param mountPath mount point path
     * @param fileName source file name
     * @param revision source revision
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Produces(YangConstants.RFC6020_YANG_MEDIA_TYPE)
    @Path("/" + URLConstants.MODULES_SUBPATH + "/{mountPath:.+}/{fileName : [^/]+}")
    public void modulesYangGET(@Encoded @PathParam("mountPath") final ApiPath mountPath,
            @PathParam("fileName") final String fileName, @QueryParam("revision") final String revision,
            @Suspended final AsyncResponse ar) {
        completeModulesGET(server.modulesYangGET(mountPath, fileName, revision), ar);
    }

    /**
     * Get schema of specific module.
     *
     * @param fileName source file name
     * @param revision source revision
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Produces(YangConstants.RFC6020_YIN_MEDIA_TYPE)
    @Path("/" + URLConstants.MODULES_SUBPATH + "/{fileName : [^/]+}")
    public void modulesYinGET(@PathParam("fileName") final String fileName,
            @QueryParam("revision") final String revision, @Suspended final AsyncResponse ar) {
        completeModulesGET(server.modulesYinGET(fileName, revision), ar);
    }

    /**
     * Get schema of specific module.
     *
     * @param mountPath mount point path
     * @param fileName source file name
     * @param revision source revision
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Produces(YangConstants.RFC6020_YIN_MEDIA_TYPE)
    @Path("/" + URLConstants.MODULES_SUBPATH + "/{mountPath:.+}/{fileName : [^/]+}")
    public void modulesYinGET(@Encoded @PathParam("mountPath") final ApiPath mountPath,
            @PathParam("fileName") final String fileName, @QueryParam("revision") final String revision,
            @Suspended final AsyncResponse ar) {
        completeModulesGET(server.modulesYinGET(mountPath, fileName, revision), ar);
    }

    private static void completeModulesGET(final RestconfFuture<ModulesGetResult> future, final AsyncResponse ar) {
        future.addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final ModulesGetResult result) throws RestconfDocumentedException {
                final Reader reader;
                try {
                    reader = result.source().openStream();
                } catch (IOException e) {
                    throw new RestconfDocumentedException("Cannot open source", e);
                }
                return Response.ok(reader).build();
            }
        });
    }
}
