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
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.ErrorTagMapping;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonChildBody;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.JsonPatchBody;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.XmlChildBody;
import org.opendaylight.restconf.server.api.XmlDataPostBody;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;
import org.opendaylight.restconf.server.api.XmlPatchBody;
import org.opendaylight.restconf.server.api.XmlResourceBody;
import org.opendaylight.restconf.server.spi.YangPatchStatusBody;
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

    private final @NonNull RestconfServer server;
    private final @NonNull PrettyPrintParam prettyPrint;
    private final @NonNull ErrorTagMapping errorTagMapping;

    public JaxRsRestconf(final RestconfServer server, final ErrorTagMapping errorTagMapping,
            final PrettyPrintParam prettyPrint) {
        this.server = requireNonNull(server);
        this.errorTagMapping = requireNonNull(errorTagMapping);
        this.prettyPrint = requireNonNull(prettyPrint);

        LOG.info("RESTCONF data-missing condition is reported as HTTP status {}", switch (errorTagMapping) {
            case ERRATA_5565 -> "404 (Errata 5565)";
            case RFC8040 -> "409 (RFC8040)";
        });
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
        server.dataDELETE(new JaxRsServerRequest<>(prettyPrint, ar) {
            @Override
            Response transform(final Empty result) {
                return Response.noContent().build();
            }
        }, identifier);
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
        server.dataGET(newDataGet(uriInfo, ar));
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
        server.dataGET(newDataGet(uriInfo, ar), identifier);
    }

    @NonNullByDefault
    private JaxRsServerRequest<DataGetResult> newDataGet(final UriInfo uriInfo, final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, ar, uriInfo) {
            @Override
            Response transform(final DataGetResult result) throws ServerException {
                final var builder = Response.ok()
                    .entity(new JaxRsFormattableBody(result.body(), prettyPrint()))
                    .cacheControl(NO_CACHE);
                fillConfigurationMetadata(builder, result);
                return builder.build();
            }
        };
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
            server.dataPATCH(newDataPATCH(ar), xmlBody);
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
            server.dataPATCH(newDataPATCH(ar), identifier, xmlBody);
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
            server.dataPATCH(newDataPATCH(ar), jsonBody);
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
            server.dataPATCH(newDataPATCH(ar), identifier, jsonBody);
        }
    }

    @NonNullByDefault
    private JaxRsServerRequest<DataPatchResult> newDataPATCH(final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, ar) {
            @Override
            Response transform(final DataPatchResult result) {
                final var builder = Response.ok();
                fillConfigurationMetadata(builder, result);
                return builder.build();
            }
        };
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_JSON)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void dataYangJsonPATCH(final InputStream body, @Context final UriInfo uriInfo,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonPatchBody(body)) {
            server.dataPATCH(newDataYangPATCH(uriInfo, ar), jsonBody);
        }
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param body YANG Patch body
     * @param uriInfo URI info
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
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonPatchBody(body)) {
            server.dataPATCH(newDataYangPATCH(uriInfo, ar), identifier, jsonBody);
        }
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link PatchStatusContext}
     */
    @PATCH
    @Path("/data")
    @Consumes(MediaTypes.APPLICATION_YANG_PATCH_XML)
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML
    })
    public void dataYangXmlPATCH(final InputStream body, @Context final UriInfo uriInfo,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            server.dataPATCH(newDataYangPATCH(uriInfo, ar), xmlBody);
        }
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param uriInfo URI info
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
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            server.dataPATCH(newDataYangPATCH(uriInfo, ar), identifier, xmlBody);
        }
    }

    @NonNullByDefault
    private JaxRsServerRequest<DataYangPatchResult> newDataYangPATCH(final UriInfo uriInfo, final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, ar, uriInfo) {
            @Override
            Response transform(final DataYangPatchResult result) {
                final var patchStatus = result.status();
                final var statusCode = statusOf(patchStatus);
                final var builder = Response.status(statusCode.code(), statusCode.phrase())
                    .entity(new YangPatchStatusBody(patchStatus));
                fillConfigurationMetadata(builder, result);
                return builder.build();
            }

            private HttpStatusCode statusOf(final PatchStatusContext result) {
                if (result.ok()) {
                    return HttpStatusCode.OK;
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
                return HttpStatusCode.INTERNAL_SERVER_ERROR;
            }

            private HttpStatusCode statusOfFirst(final List<ServerError> error) {
                return errorTagMapping.statusOf(error.get(0).tag());
            }
        };
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
            server.dataPOST(newDataPOST(uriInfo, ar), jsonBody);
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
        server.dataPOST(newDataPOST(uriInfo, ar), identifier, new JsonDataPostBody(body));
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
            server.dataPOST(newDataPOST(uriInfo, ar), xmlBody);
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
        server.dataPOST(newDataPOST(uriInfo, ar), identifier, new XmlDataPostBody(body));
    }

    @NonNullByDefault
    private <T extends DataPostResult> JaxRsServerRequest<T> newDataPOST(final UriInfo uriInfo,
            final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, ar, uriInfo) {
            @Override
            Response transform(final DataPostResult result) {
                if (result instanceof CreateResourceResult createResource) {
                    final var builder = Response.created(uriInfo.getBaseUriBuilder()
                        .path("data")
                        .path(createResource.createdPath().toString())
                        .build());
                    fillConfigurationMetadata(builder, createResource);
                    return builder.build();
                }
                if (result instanceof InvokeResult invokeOperation) {
                    final var output = invokeOperation.output();
                    return output == null ? Response.noContent().build()
                        : Response.ok().entity(new JaxRsFormattableBody(output, prettyPrint())).build();
                }
                LOG.error("Unhandled result {}", result);
                return Response.serverError().build();
            }
        };
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
            server.dataPUT(newDataPUT(uriInfo, ar), jsonBody);
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
            server.dataPUT(newDataPUT(uriInfo, ar), identifier, jsonBody);
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
            server.dataPUT(newDataPUT(uriInfo, ar), xmlBody);
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
            server.dataPUT(newDataPUT(uriInfo, ar), identifier, xmlBody);
        }
    }

    @NonNullByDefault
    private JaxRsServerRequest<DataPutResult> newDataPUT(final UriInfo uriInfo, final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, ar, uriInfo) {
            @Override
            Response transform(final DataPutResult result) {
                // Note: no Location header, as it matches the request path
                final var builder = result.created() ? Response.created(null) : Response.noContent();
                fillConfigurationMetadata(builder, result);
                return builder.build();
            }
        };
    }

    /**
     * List RPC and action operations.
     *
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Path("/operations")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML,
        MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON
    })
    public void operationsGET(@Suspended final AsyncResponse ar) {
        server.operationsGET(new FormattableJaxRsServerRequest(prettyPrint, ar));
    }

    /**
     * Retrieve list of operations and actions supported by the server or device.
     *
     * @param operation path parameter to identify device and/or operation
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Path("/operations/{operation:.+}")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML,
        MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON
    })
    public void operationsGET(@PathParam("operation") final ApiPath operation, @Suspended final AsyncResponse ar) {
        server.operationsGET(new FormattableJaxRsServerRequest(prettyPrint, ar), operation);
    }

    /**
     * Invoke RPC operation.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param body the body of the operation
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link FormattableBody} output
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
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link FormattableBody} output
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

    @NonNullByDefault
    private void operationsPOST(final ApiPath identifier, final UriInfo uriInfo, final AsyncResponse ar,
            final OperationInputBody body) {
        server.operationsPOST(new JaxRsServerRequest<>(prettyPrint, ar, uriInfo) {
            @Override
            Response transform(final InvokeResult result) {
                final var body = result.output();
                return body == null ? Response.noContent().build()
                    : Response.ok().entity(new JaxRsFormattableBody(body, prettyPrint())).build();
            }
        }, uriInfo.getBaseUri(), identifier, body);
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
        server.yangLibraryVersionGET(new FormattableJaxRsServerRequest(prettyPrint, ar));
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
        server.modulesYangGET(newModulesGET(ar), fileName, revision);
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
        server.modulesYangGET(newModulesGET(ar), mountPath, fileName, revision);
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
        server.modulesYinGET(newModulesGET(ar), fileName, revision);
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
        server.modulesYinGET(newModulesGET(ar), mountPath, fileName, revision);
    }

    @NonNullByDefault
    private JaxRsServerRequest<ModulesGetResult> newModulesGET(final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, ar) {
            @Override
            Response transform(final ModulesGetResult result) throws ServerException {
                final Reader reader;
                try {
                    reader = result.source().openStream();
                } catch (IOException e) {
                    throw new ServerException("Cannot open source", e);
                }
                return Response.ok(reader).build();
            }
        };
    }
}
