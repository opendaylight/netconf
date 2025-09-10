/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

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
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.OPTIONS;
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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonChildBody;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.JsonPatchBody;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.OptionsResult;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.XmlChildBody;
import org.opendaylight.restconf.server.api.XmlDataPostBody;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;
import org.opendaylight.restconf.server.api.XmlPatchBody;
import org.opendaylight.restconf.server.api.XmlResourceBody;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.YangPatchStatusBody;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
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
    private final RestconfStream.@NonNull Registry streamRegistry;
    private final @NonNull SSESenderFactory senderFactory;
    private final @NonNull PrettyPrintParam prettyPrint;
    private final @NonNull ErrorTagMapping errorTagMapping;
    /**
     * The second URL path element for YANG library module support, i.e. {@code https://localhost/BASE_PATH/MODULES}.
     */
    static final String MODULES_SUBPATH = "modules";
    /**
     * The second URL path element for streams support, i.e. {@code https://localhost/BASE_PATH/STREAMS}.
     */
    public static final String STREAMS_SUBPATH = "streams";
    /**
     * The query parameter carrying the optional revision in YANG library module support, i.e.
     * {@code https://localhost/BASE_PATH/MODULES?REVISION=2023-11-26}.
     */
    static final String MODULES_REVISION_QUERY = "revision";

    public JaxRsRestconf(final RestconfServer server, final RestconfStream.Registry streamRegistry,
            final SSESenderFactory senderFactory, final ErrorTagMapping errorTagMapping,
            final PrettyPrintParam prettyPrint) {
        this.server = requireNonNull(server);
        this.streamRegistry = requireNonNull(streamRegistry);
        this.senderFactory = requireNonNull(senderFactory);
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
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @DELETE
    @Path("/data/{identifier:.+}")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public void dataDELETE(@Encoded @PathParam("identifier") final ApiPath identifier,
            final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.dataDELETE(new JaxRsServerRequest<>(prettyPrint, errorTagMapping, sc, ar) {
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
     * @param sc {@link SecurityContext} of the request
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
    public void dataGET(@Context final UriInfo uriInfo, final @Context SecurityContext sc,
            @Suspended final AsyncResponse ar) {
        server.dataGET(newDataGet(uriInfo, sc, ar));
    }

    /**
     * Get target data resource.
     *
     * @param identifier path to target
     * @param uriInfo URI info
     * @param sc {@link SecurityContext} of the request
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
            @Context final SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.dataGET(newDataGet(uriInfo, sc, ar), identifier);
    }

    @NonNullByDefault
    private JaxRsServerRequest<DataGetResult> newDataGet(final UriInfo uriInfo, final SecurityContext sc,
            final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, errorTagMapping, sc, ar, uriInfo) {
            @Override
            Response transform(final DataGetResult result) throws RequestException {
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

    @OPTIONS
    @Path("/data")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public void dataOPTIONS(@Context final UriInfo uriInfo, final @Context SecurityContext sc,
            @Suspended final AsyncResponse ar) {
        server.dataOPTIONS(newOptions(uriInfo, sc, ar));
    }

    @OPTIONS
    @Path("/data/{identifier:.+}")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public void dataOPTIONS(@Encoded @PathParam("identifier") final ApiPath identifier, @Context final UriInfo uriInfo,
            @Context final SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.dataOPTIONS(newOptions(uriInfo, sc, ar), identifier);
    }

    @NonNullByDefault
    private JaxRsServerRequest<OptionsResult> newOptions(final UriInfo uriInfo, final SecurityContext sc,
            final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, errorTagMapping, sc, ar, uriInfo) {
            private static final String ACCEPT_PATCH = String.join(", ",
                // RESTCONF-defined
                MediaTypes.APPLICATION_YANG_DATA_JSON,
                MediaTypes.APPLICATION_YANG_DATA_XML,
                MediaTypes.APPLICATION_YANG_PATCH_JSON,
                MediaTypes.APPLICATION_YANG_PATCH_XML,
                // compatibility
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_XML,
                MediaType.TEXT_XML);

            @Override
            Response transform(final OptionsResult result) {
                return switch (result) {
                    case ACTION -> Response.ok().header(HttpHeaders.ALLOW, "OPTIONS, POST").build();
                    case DATASTORE -> allowMethods("GET, HEAD, OPTIONS, PATCH, POST, PUT");
                    case RESOURCE -> allowMethods("DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT");
                    case READ_ONLY -> allowGetHeadOptions();
                    case RPC -> Response.ok().header(HttpHeaders.ALLOW, "GET, HEAD, OPTIONS, POST").build();
                };
            }

            private static Response allowMethods(final String allow) {
                return Response.ok().header(HttpHeaders.ALLOW, allow).header("Accept-Patch", ACCEPT_PATCH).build();
            }
        };
    }

    /**
     * Partially modify the target data store, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param body data node for put to config DS
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void dataXmlPATCH(final InputStream body, final @Context SecurityContext sc,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            server.dataPATCH(newDataPATCH(sc, ar), xmlBody);
        }
    }

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param sc {@link SecurityContext} of the request
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
            final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            server.dataPATCH(newDataPATCH(sc, ar), identifier, xmlBody);
        }
    }

    /**
     * Partially modify the target data store, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param body data node for put to config DS
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void dataJsonPATCH(final InputStream body, final @Context SecurityContext sc,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            server.dataPATCH(newDataPATCH(sc, ar), jsonBody);
        }
    }

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void dataJsonPATCH(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
            final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            server.dataPATCH(newDataPATCH(sc, ar), identifier, jsonBody);
        }
    }

    @NonNullByDefault
    private JaxRsServerRequest<DataPatchResult> newDataPATCH(final SecurityContext sc, final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, errorTagMapping, sc, ar) {
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
     * @param sc {@link SecurityContext} of the request
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
            final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonPatchBody(body)) {
            server.dataPATCH(newDataYangPATCH(uriInfo, sc, ar), jsonBody);
        }
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param body YANG Patch body
     * @param uriInfo URI info
     * @param sc {@link SecurityContext} of the request
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
            @Context final UriInfo uriInfo, final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonPatchBody(body)) {
            server.dataPATCH(newDataYangPATCH(uriInfo, sc, ar), identifier, jsonBody);
        }
    }

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @param uriInfo URI info
     * @param sc {@link SecurityContext} of the request
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
            final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            server.dataPATCH(newDataYangPATCH(uriInfo, sc, ar), xmlBody);
        }
    }

    /**
     * Ordered list of edits that are applied to the target datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param uriInfo URI info
     * @param body YANG Patch body
     * @param sc {@link SecurityContext} of the request
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
            @Context final UriInfo uriInfo, final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlPatchBody(body)) {
            server.dataPATCH(newDataYangPATCH(uriInfo, sc, ar), identifier, xmlBody);
        }
    }

    @NonNullByDefault
    private JaxRsServerRequest<DataYangPatchResult> newDataYangPATCH(final UriInfo uriInfo, final SecurityContext sc,
            final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, errorTagMapping, sc, ar, uriInfo) {
            @Override
            Response transform(final DataYangPatchResult result) {
                final var patchStatus = result.status();
                final var statusCode = statusOf(patchStatus);
                final var builder = Response.status(statusCode.code(), statusCode.phrase())
                    .entity(new JaxRsFormattableBody(new YangPatchStatusBody(patchStatus), prettyPrint()));
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

            private HttpStatusCode statusOfFirst(final List<RequestError> error) {
                return errorTagMapping.statusOf(error.get(0).tag());
            }
        };
    }

    /**
     * Create a top-level data resource.
     *
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void postDataJSON(final InputStream body, @Context final UriInfo uriInfo, final @Context SecurityContext sc,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonChildBody(body)) {
            server.dataPOST(newDataPOST(uriInfo, sc, ar, EncodeJson$I.QNAME), jsonBody);
        }
    }

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void postDataJSON(@Encoded @PathParam("identifier") final ApiPath identifier, final InputStream body,
            @Context final UriInfo uriInfo, final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.dataPOST(newDataPOST(uriInfo, sc, ar, EncodeJson$I.QNAME), identifier,
            new JsonDataPostBody(body));
    }

    /**
     * Create a top-level data resource.
     *
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @POST
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void postDataXML(final InputStream body, @Context final UriInfo uriInfo, final @Context SecurityContext sc,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlChildBody(body)) {
            server.dataPOST(newDataPOST(uriInfo, sc, ar, EncodeXml$I.QNAME), xmlBody);
        }
    }

    /**
     * Create a data resource in target.
     *
     * @param identifier path to target
     * @param body data node for put to config DS
     * @param uriInfo URI info
     * @param sc {@link SecurityContext} of the request
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
            @Context final UriInfo uriInfo, final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.dataPOST(newDataPOST(uriInfo, sc, ar, EncodeXml$I.QNAME), identifier,
            new XmlDataPostBody(body));
    }

    @NonNullByDefault
    private <T extends DataPostResult> JaxRsServerRequest<T> newDataPOST(final UriInfo uriInfo,
            final SecurityContext sc, final AsyncResponse ar, final QName contentEncoding) {
        return new JaxRsServerRequest<>(prettyPrint, errorTagMapping, sc, ar, uriInfo) {
            @Override
            Response transform(final DataPostResult result) {
                return switch (result) {
                    case CreateResourceResult createResource -> {
                        final var builder = Response.created(uriInfo.getBaseUriBuilder()
                            .path("data")
                            .path(createResource.createdPath().toString())
                            .build());
                        fillConfigurationMetadata(builder, createResource);
                        yield builder.build();
                    }
                    case InvokeResult invokeOperation -> {
                        final var output = invokeOperation.output();
                        yield output == null ? Response.noContent().build()
                            : Response.ok().entity(new JaxRsFormattableBody(output, prettyPrint())).build();
                    }
                };
            }

            @Override
            public @Nullable QName contentEncoding() {
                return contentEncoding;
            }
        };
    }

    /**
     * Replace the data store.
     *
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void dataJsonPUT(@Context final UriInfo uriInfo, final @Context SecurityContext sc, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            server.dataPUT(newDataPUT(uriInfo, sc, ar), jsonBody);
        }
    }

    /**
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    public void dataJsonPUT(@Encoded @PathParam("identifier") final ApiPath identifier, @Context final UriInfo uriInfo,
            final @Context SecurityContext sc, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonResourceBody(body)) {
            server.dataPUT(newDataPUT(uriInfo, sc, ar), identifier, jsonBody);
        }
    }

    /**
     * Replace the data store.
     *
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @PUT
    @Path("/data")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void dataXmlPUT(@Context final UriInfo uriInfo, final @Context SecurityContext sc, final InputStream body,
            @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            server.dataPUT(newDataPUT(uriInfo, sc, ar), xmlBody);
        }
    }

    /**
     * Create or replace the target data resource.
     *
     * @param identifier path to target
     * @param uriInfo request URI information
     * @param body data node for put to config DS
     * @param sc {@link SecurityContext} of the request
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
            final @Context SecurityContext sc, final InputStream body, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlResourceBody(body)) {
            server.dataPUT(newDataPUT(uriInfo, sc, ar), identifier, xmlBody);
        }
    }

    @NonNullByDefault
    private JaxRsServerRequest<DataPutResult> newDataPUT(final UriInfo uriInfo, final SecurityContext sc,
            final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, errorTagMapping, sc, ar, uriInfo) {
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
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Path("/operations")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML,
        MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON
    })
    public void operationsGET(final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.operationsGET(new FormattableJaxRsServerRequest(prettyPrint, errorTagMapping, sc, ar));
    }

    /**
     * Retrieve list of operations and actions supported by the server or device.
     *
     * @param operation path parameter to identify device and/or operation
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Path("/operations/{operation:.+}")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML,
        MediaTypes.APPLICATION_YANG_DATA_JSON, MediaType.APPLICATION_JSON
    })
    public void operationsGET(@PathParam("operation") final ApiPath operation, final @Context SecurityContext sc,
            @Suspended final AsyncResponse ar) {
        server.operationsGET(new FormattableJaxRsServerRequest(prettyPrint, errorTagMapping, sc, ar), operation);
    }

    @OPTIONS
    @Path("/operations")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public Response operationsOPTIONS() {
        return allowGetHeadOptions();
    }

    @OPTIONS
    @Path("/operations/{operation:.+}")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public void operationsOPTIONS(@Encoded @PathParam("operation") final ApiPath operation,
            @Context final UriInfo uriInfo, @Context final SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.operationsOPTIONS(newOptions(uriInfo, sc, ar), operation);
    }

    /**
     * Invoke RPC operation.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param body the body of the operation
     * @param uriInfo URI info
     * @param sc {@link SecurityContext} of the request
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
            @Context final UriInfo uriInfo, final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlOperationInputBody(body)) {
            operationsPOST(identifier, uriInfo, sc, ar, xmlBody, EncodeXml$I.QNAME);
        }
    }

    /**
     * Invoke RPC operation.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param body the body of the operation
     * @param uriInfo URI info
     * @param sc {@link SecurityContext} of the request
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
            @Context final UriInfo uriInfo, final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonOperationInputBody(body)) {
            operationsPOST(identifier, uriInfo, sc, ar, jsonBody, EncodeJson$I.QNAME);
        }
    }

    @NonNullByDefault
    private void operationsPOST(final ApiPath identifier, final UriInfo uriInfo, final SecurityContext sc,
            final AsyncResponse ar, final OperationInputBody body, final QName contentEncoding) {
        server.operationsPOST(new JaxRsServerRequest<>(prettyPrint, errorTagMapping, sc, ar, uriInfo) {
            @Override
            Response transform(final InvokeResult result) {
                final var body = result.output();
                return body == null ? Response.noContent().build()
                    : Response.ok().entity(new JaxRsFormattableBody(body, prettyPrint())).build();
            }

            @Override
            public @Nullable QName contentEncoding() {
                return contentEncoding;
            }
        }, uriInfo.getBaseUri(), identifier, body);
    }

    /**
     * Get revision of IETF YANG Library module.
     *
     * @param sc {@link SecurityContext} of the request
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
    public void yangLibraryVersionGET(final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.yangLibraryVersionGET(new FormattableJaxRsServerRequest(prettyPrint, errorTagMapping, sc, ar));
    }

    @OPTIONS
    @Path("/yang-library-version")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public Response yangLibraryVersionOPTIONS() {
        return allowGetHeadOptions();
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
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Produces(YangConstants.RFC6020_YANG_MEDIA_TYPE)
    @Path("/" + MODULES_SUBPATH + "/{fileName : [^/]+}")
    public void modulesYangGET(@PathParam("fileName") final String fileName,
            @QueryParam("revision") final String revision, final @Context SecurityContext sc,
            @Suspended final AsyncResponse ar) {
        server.modulesYangGET(newModulesGET(sc, ar), fileName, revision);
    }

    /**
     * Get schema of specific module.
     *
     * @param mountPath mount point path
     * @param fileName source file name
     * @param revision source revision
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Produces(YangConstants.RFC6020_YANG_MEDIA_TYPE)
    @Path("/" + MODULES_SUBPATH + "/{mountPath:.+}/{fileName : [^/]+}")
    public void modulesYangGET(@Encoded @PathParam("mountPath") final ApiPath mountPath,
            @PathParam("fileName") final String fileName, @QueryParam("revision") final String revision,
            final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.modulesYangGET(newModulesGET(sc, ar), mountPath, fileName, revision);
    }

    /**
     * Get schema of specific module.
     *
     * @param fileName source file name
     * @param revision source revision
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Produces(YangConstants.RFC6020_YIN_MEDIA_TYPE)
    @Path("/" + MODULES_SUBPATH + "/{fileName : [^/]+}")
    public void modulesYinGET(@PathParam("fileName") final String fileName,
            @QueryParam("revision") final String revision, final @Context SecurityContext sc,
            @Suspended final AsyncResponse ar) {
        server.modulesYinGET(newModulesGET(sc, ar), fileName, revision);
    }

    /**
     * Get schema of specific module.
     *
     * @param mountPath mount point path
     * @param fileName source file name
     * @param revision source revision
     * @param sc {@link SecurityContext} of the request
     * @param ar {@link AsyncResponse} which needs to be completed
     */
    @GET
    @Produces(YangConstants.RFC6020_YIN_MEDIA_TYPE)
    @Path("/" + MODULES_SUBPATH + "/{mountPath:.+}/{fileName : [^/]+}")
    public void modulesYinGET(@Encoded @PathParam("mountPath") final ApiPath mountPath,
            @PathParam("fileName") final String fileName, @QueryParam("revision") final String revision,
            final @Context SecurityContext sc, @Suspended final AsyncResponse ar) {
        server.modulesYinGET(newModulesGET(sc, ar), mountPath, fileName, revision);
    }

    @OPTIONS
    @Path("/" + MODULES_SUBPATH + "/{mountPath:.+}/{fileName : [^/]+}")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public Response modulesOPTIONS(@Encoded @PathParam("mountPath") final ApiPath mountPath) {
        return allowGetHeadOptions();
    }

    @OPTIONS
    @Path("/" + MODULES_SUBPATH + "/{fileName : [^/]+}")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public Response modulesOPTIONS() {
        return allowGetHeadOptions();
    }

    @NonNullByDefault
    private JaxRsServerRequest<ModulesGetResult> newModulesGET(final SecurityContext sc, final AsyncResponse ar) {
        return new JaxRsServerRequest<>(prettyPrint, errorTagMapping, sc, ar) {
            @Override
            Response transform(final ModulesGetResult result) throws RequestException {
                final Reader reader;
                try {
                    reader = result.source().openStream();
                } catch (IOException e) {
                    throw new RequestException("Cannot open source", e);
                }
                return Response.ok(reader).build();
            }
        };
    }

    /**
     * Attach to a particular notification stream.
     *
     * @param streamName path to target
     */
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/" + STREAMS_SUBPATH + "/{encodingName:[a-zA-Z]+}/{streamName:.+}")
    public void streamsGET(@PathParam("encodingName") final EncodingName encodingName,
            @PathParam("streamName") final String streamName, @Context final UriInfo uriInfo,
            @Context final SseEventSink sink, @Context final Sse sse) {
        final var stream = streamRegistry.lookupStream(streamName);
        if (stream == null) {
            LOG.debug("Listener for stream with name {} was not found.", streamName);
            throw new NotFoundException("No such stream: " + streamName);
        }

        final EventStreamGetParams getParams;
        try {
            getParams = EventStreamGetParams.of(QueryParameters.ofMultiValue(uriInfo.getQueryParameters()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }

        LOG.debug("Listener for stream with name {} has been found, SSE session handler will be created.", streamName);
        senderFactory.newSSESender(sink, sse, stream, encodingName, getParams);
    }

    @OPTIONS
    @Path("/" + STREAMS_SUBPATH + "/{encodingName:[a-zA-Z]+}/{streamName:.+}")
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    public Response streamsOPTIONS(@PathParam("encodingName") final EncodingName encodingName) {
        return allowGetHeadOptions();
    }

    private static @NonNull Response allowGetHeadOptions() {
        return Response.ok().header(HttpHeaders.ALLOW, "GET, HEAD, OPTIONS").build();
    }
}
