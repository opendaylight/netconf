/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.NettyMediaTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.NettyMediaTypes.YANG_PATCH_TYPES;
import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.ResponseUtils.responseStatus;
import static org.opendaylight.restconf.server.ResponseUtils.simpleErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unmappedRequestErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unsupportedMediaTypeErrorResponse;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
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
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.XmlChildBody;
import org.opendaylight.restconf.server.api.XmlDataPostBody;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;
import org.opendaylight.restconf.server.api.XmlPatchBody;
import org.opendaylight.restconf.server.api.XmlResourceBody;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.YangPatchStatusBody;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RestconfRequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequestDispatcher.class);

    @VisibleForTesting
    static final String REVISION = "revision";
    @VisibleForTesting
    static final String MISSING_FILENAME_ERROR = "Module name is missing";
    @VisibleForTesting
    static final String SOURCE_READ_FAILURE_ERROR = "Failure reading module source: ";

    private final URI baseUri;
    private final RestconfServer server;
    private final PrincipalService principalService;
    private final ErrorTagMapping errorTagMapping;
    private final AsciiString defaultAcceptType;
    private final PrettyPrintParam defaultPrettyPrint;

    // baseUri.getPath(), i.e. "{+restconf}"
    private final String plusRestconf;
    // cached plusRestconf.length()
    private final int plusRestconfLen;

    RestconfRequestDispatcher(final RestconfServer server, final PrincipalService principalService,
            final URI baseUri, final ErrorTagMapping errorTagMapping, final AsciiString defaultAcceptType,
            final PrettyPrintParam defaultPrettyPrint) {
        this.server = requireNonNull(server);
        this.principalService = requireNonNull(principalService);
        this.baseUri = requireNonNull(baseUri);
        this.errorTagMapping = requireNonNull(errorTagMapping);
        this.defaultAcceptType = requireNonNull(defaultAcceptType);
        this.defaultPrettyPrint = requireNonNull(defaultPrettyPrint);

        plusRestconf = baseUri.getPath();
        plusRestconfLen = plusRestconf.length();

        LOG.info("{} initialized with service {}", getClass().getSimpleName(), server.getClass());
        LOG.info("Base path: {}, default accept: {}, default pretty print: {}",
            baseUri, defaultAcceptType, defaultPrettyPrint.value());
    }

    @SuppressWarnings("IllegalCatch")
    void dispatch(final QueryStringDecoder decoder, final FullHttpRequest request, final RestconfRequest callback) {
        LOG.debug("Dispatching {} {}", request.method(), request.uri());

        final var path = decoder.path();
        if (!path.startsWith(plusRestconf)) {
            callback.onSuccess(notFound(request));
            return;
        }

        final var suffix = path.substring(plusRestconfLen);
        // TODO: are '/restconf' and '/restconf/' the same thing? for now we treat them as such for now
        if (suffix.isEmpty() || suffix.equals("/")) {
            // FIXME: we are rejecting requests to '{+restconf}', which matches JAX-RS server behaviour, but is not
            //        correct: we should be reporting the entire API Resource, as described in
            //        https://www.rfc-editor.org/rfc/rfc8040#section-3.3
            callback.onSuccess(notFound(request));
            return;
        }

        if (!suffix.startsWith("/")) {
            callback.onSuccess(notFound(request));
            return;
        }

        final var principal = principalService.acquirePrincipal(request);
        final var params = new RequestParameters(baseUri, decoder, request, principal, errorTagMapping,
            defaultAcceptType, defaultPrettyPrint);
        try {
            switch (params.pathParameters().apiResource()) {
                case PathParameters.DATA -> processDataRequest(params, callback);
                case PathParameters.OPERATIONS -> processOperationsRequest(params, callback);
                case PathParameters.YANG_LIBRARY_VERSION -> processYangLibraryVersion(params, callback);
                case PathParameters.MODULES -> processModules(params, callback);
                default -> callback.onSuccess(
                    HttpMethod.OPTIONS.equals(params.method())
                        ? optionsResponse(params, HttpMethod.OPTIONS.name()) : notFound(request));
            }
        } catch (RuntimeException e) {
            LOG.error("Error processing request {} {}", request.method(), request.uri(), e);
            final var errorTag = e instanceof ServerErrorException see ? see.errorTag() : ErrorTag.OPERATION_FAILED;
            callback.onSuccess(simpleErrorResponse(params, errorTag, e.getMessage()));
        }
    }

    @NonNullByDefault
    private static FullHttpResponse notFound(final FullHttpRequest request) {
        return new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.NOT_FOUND);
    }

    /**
     * Process a request to <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.1">RFC 8040 {+restconf}/data</a>
     * resource.
     */
    private void processDataRequest(final RequestParameters params, final RestconfRequest callback) {
        final var contentType = params.contentType();
        final var apiPath = extractApiPath(params);
        switch (params.method().name()) {
            // resource options -> https://www.rfc-editor.org/rfc/rfc8040#section-4.1
            case "OPTIONS" -> {
                final var request = new OptionsServerRequest(params, callback);
                if (apiPath.isEmpty()) {
                    server.dataOPTIONS(request);
                } else {
                    server.dataOPTIONS(request, apiPath);
                }
            }
            // retrieve data and metadata for a resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.3
            // HEAD is same as GET but without content -> https://www.rfc-editor.org/rfc/rfc8040#section-4.2
            case "HEAD", "GET" -> getData(params, callback, apiPath);
            case "POST" -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
                    // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
                    postData(params, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            case "PUT" -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // create or replace target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.5
                    putData(params, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            case "PATCH" -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // Plain RESTCONF patch = merge target resource content ->
                    // https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1
                    patchData(params, callback, apiPath);
                } else if (YANG_PATCH_TYPES.contains(contentType)) {
                    // YANG Patch = ordered list of edits that are applied to the target datastore ->
                    // https://www.rfc-editor.org/rfc/rfc8072#section-2
                    yangPatchData(params, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            // delete target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.7
            case "DELETE" -> deleteData(params, callback, apiPath);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private void getData(final RequestParameters params, final RestconfRequest callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataGetResult>(params, callback) {
            @Override
            FullHttpResponse transform(final DataGetResult result) {
                return responseBuilder(requestParams, HttpResponseStatus.OK)
                    .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
                    .setMetadataHeaders(result)
                    .setBody(result.body())
                    .build();
            }
        };

        if (apiPath.isEmpty()) {
            server.dataGET(request);
        } else {
            server.dataGET(request, apiPath);
        }
    }

    private void postData(final RequestParameters params, final RestconfRequest callback, final ApiPath apiPath) {
        if (apiPath.isEmpty()) {
            server.dataPOST(postRequest(params, callback),
                requestBody(params, JsonChildBody::new, XmlChildBody::new));
        } else {
            server.dataPOST(postRequest(params, callback), apiPath,
                requestBody(params, JsonDataPostBody::new, XmlDataPostBody::new));
        }
    }

    private static <T extends DataPostResult> ServerRequest<T> postRequest(final RequestParameters params,
            final RestconfRequest callback) {
        return new NettyServerRequest<>(params, callback) {
            @Override
            FullHttpResponse transform(final DataPostResult result) {
                return switch (result) {
                    case CreateResourceResult createResult -> {
                        yield responseBuilder(requestParams, HttpResponseStatus.CREATED)
                            .setHeader(HttpHeaderNames.LOCATION,
                                requestParams.baseUri() + PathParameters.DATA + "/" + createResult.createdPath())
                            .setMetadataHeaders(createResult)
                            .build();
                    }
                    case InvokeResult invokeResult -> {
                        final var output = invokeResult.output();
                        yield output == null ? simpleResponse(requestParams, HttpResponseStatus.NO_CONTENT)
                            : responseBuilder(requestParams, HttpResponseStatus.OK).setBody(output).build();
                    }
                };
            }
        };
    }

    private void putData(final RequestParameters params, final RestconfRequest callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataPutResult>(params, callback) {
            @Override
            FullHttpResponse transform(final DataPutResult result) {
                final var status = result.created() ? HttpResponseStatus.CREATED : HttpResponseStatus.NO_CONTENT;
                return responseBuilder(requestParams, status).setMetadataHeaders(result).build();
            }
        };
        final var dataResourceBody = requestBody(params, JsonResourceBody::new, XmlResourceBody::new);
        if (apiPath.isEmpty()) {
            server.dataPUT(request, dataResourceBody);
        } else {
            server.dataPUT(request, apiPath, dataResourceBody);
        }
    }

    private void patchData(final RequestParameters params, final RestconfRequest callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataPatchResult>(params, callback) {
            @Override
            FullHttpResponse transform(final DataPatchResult result) {
                return responseBuilder(requestParams, HttpResponseStatus.OK).setMetadataHeaders(result).build();
            }
        };
        final var dataResourceBody = requestBody(params, JsonResourceBody::new, XmlResourceBody::new);
        if (apiPath.isEmpty()) {
            server.dataPATCH(request, dataResourceBody);
        } else {
            server.dataPATCH(request, apiPath, dataResourceBody);
        }
    }

    private void yangPatchData(final RequestParameters params, final RestconfRequest callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataYangPatchResult>(params, callback) {
            @Override
            FullHttpResponse transform(final DataYangPatchResult result) {
                final var patchStatus = result.status();
                return responseBuilder(requestParams, patchResponseStatus(patchStatus, requestParams.errorTagMapping()))
                    .setBody(new YangPatchStatusBody(patchStatus))
                    .setMetadataHeaders(result)
                    .build();
            }
        };
        final var yangPatchBody = requestBody(params, JsonPatchBody::new, XmlPatchBody::new);
        if (apiPath.isEmpty()) {
            server.dataPATCH(request, yangPatchBody);
        } else {
            server.dataPATCH(request, apiPath, yangPatchBody);
        }
    }

    private static HttpResponseStatus patchResponseStatus(final PatchStatusContext statusContext,
        final ErrorTagMapping errorTagMapping) {
        if (statusContext.ok()) {
            return HttpResponseStatus.OK;
        }
        final var globalErrors = statusContext.globalErrors();
        if (globalErrors != null && !globalErrors.isEmpty()) {
            return responseStatus(globalErrors.getFirst().tag(), errorTagMapping);
        }
        for (var edit : statusContext.editCollection()) {
            if (!edit.isOk()) {
                final var editErrors = edit.getEditErrors();
                if (editErrors != null && !editErrors.isEmpty()) {
                    return responseStatus(editErrors.getFirst().tag(), errorTagMapping);
                }
            }
        }
        return HttpResponseStatus.INTERNAL_SERVER_ERROR;
    }

    private void deleteData(final RequestParameters params, final RestconfRequest callback, final ApiPath apiPath) {
        server.dataDELETE(new NettyServerRequest<>(params, callback) {
            @Override
            FullHttpResponse transform(final Empty result) {
                return simpleResponse(requestParams, HttpResponseStatus.NO_CONTENT);
            }
        }, apiPath);
    }

    /**
     * Process a request to
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC 8040 {+restconf}/operations</a> resource.
     */
    private void processOperationsRequest(final RequestParameters params, final RestconfRequest callback) {
        final var apiPath = extractApiPath(params);
        switch (params.method().name()) {
            case "OPTIONS" -> {
                if (apiPath.isEmpty()) {
                    callback.onSuccess(OptionsServerRequest.withoutPatch(params.protocolVersion(),
                        "GET, HEAD, OPTIONS"));
                } else {
                    server.operationsOPTIONS(new OptionsServerRequest(params, callback), apiPath);
                }
            }
            case "HEAD", "GET" -> getOperations(params, callback, apiPath);
            case "POST" -> {
                if (NettyMediaTypes.RESTCONF_TYPES.contains(params.contentType())) {
                    // invoke rpc -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2
                    postOperations(params, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private void getOperations(final RequestParameters params, final RestconfRequest callback, final ApiPath apiPath) {
        final var request = new FormattableServerRequest(params, callback);
        if (apiPath.isEmpty()) {
            server.operationsGET(request);
        } else {
            server.operationsGET(request, apiPath);
        }
    }

    private void postOperations(final RequestParameters params, final RestconfRequest callback,
            final ApiPath apiPath) {
        server.operationsPOST(new NettyServerRequest<>(params, callback) {
            @Override
            FullHttpResponse transform(final InvokeResult result) {
                final var output = result.output();
                return output == null ? simpleResponse(requestParams, HttpResponseStatus.NO_CONTENT)
                    : responseBuilder(requestParams, HttpResponseStatus.OK).setBody(output).build();
            }
        }, restconfUri(params.baseUri()), apiPath,
            requestBody(params, JsonOperationInputBody::new, XmlOperationInputBody::new));
    }

    private static URI restconfUri(final URI uri) {
        // we need to add `/` at the end of RESTCONF base path to create correct streams URLs
        return URI.create(uri.toString().concat("/"));
    }

    /**
     * Process a request to
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">{+restconf}/yang-library-version</a> resource.
     */
    private void processYangLibraryVersion(final RequestParameters params, final RestconfRequest callback) {
        switch (params.method().name()) {
            case "OPTIONS" -> callback.onSuccess(optionsResponse(params, "GET, HEAD, OPTIONS"));
            case "HEAD", "GET" -> server.yangLibraryVersionGET(new FormattableServerRequest(params, callback));
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    /**
     * Access to YANG modules.
     */
    private void processModules(final RequestParameters params, final RestconfRequest callback) {
        switch (params.method().name()) {
            case "OPTIONS" -> callback.onSuccess(optionsResponse(params, "GET, HEAD, OPTIONS"));
            case "HEAD", "GET" -> getModule(params, callback);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private void getModule(final RequestParameters params, final RestconfRequest callback) {
        final var file = extractModuleFile(params.pathParameters().childIdentifier());
        final var revision = params.queryParameters().lookup(REVISION);
        if (file.name().isEmpty()) {
            callback.onSuccess(simpleErrorResponse(params, ErrorTag.MISSING_ELEMENT, MISSING_FILENAME_ERROR));
            return;
        }
        final var acceptYang = params.requestHeaders()
            .contains(HttpHeaderNames.ACCEPT, NettyMediaTypes.APPLICATION_YANG, true);
        final var acceptYin = params.requestHeaders()
            .contains(HttpHeaderNames.ACCEPT, NettyMediaTypes.APPLICATION_YIN_XML, true);
        if (acceptYin && !acceptYang) {
            // YIN if explicitly requested
            final var request = getModuleRequest(params, callback, NettyMediaTypes.APPLICATION_YIN_XML);
            if (file.mountPath.isEmpty()) {
                server.modulesYinGET(request, file.name(), revision);
            } else {
                server.modulesYinGET(request, file.mountPath(), file.name(), revision);
            }
        } else {
            // YANG by default, incl accept any
            final var request = getModuleRequest(params, callback, NettyMediaTypes.APPLICATION_YANG);
            if (file.mountPath.isEmpty()) {
                server.modulesYangGET(request, file.name(), revision);
            } else {
                server.modulesYangGET(request, file.mountPath(), file.name(), revision);
            }
        }
    }

    private static ServerRequest<ModulesGetResult> getModuleRequest(final RequestParameters params,
            final RestconfRequest callback, final AsciiString mediaType) {
        return new NettyServerRequest<>(params, callback) {
            @Override
            FullHttpResponse transform(final ModulesGetResult result) {
                final byte[] bytes;
                try {
                    bytes = result.source().asByteSource(StandardCharsets.UTF_8).read();
                } catch (IOException e) {
                    throw new ServerErrorException(ErrorTag.OPERATION_FAILED,
                        SOURCE_READ_FAILURE_ERROR + e.getMessage(), e);
                }
                return simpleResponse(requestParams, HttpResponseStatus.OK, mediaType, bytes);
            }
        };
    }

    private static ModuleFile extractModuleFile(final String path) {
        // optional mountPath followed by file name separated by slash
        final var lastIndex = path.length() - 1;
        final var splitIndex = path.lastIndexOf('/');
        if (splitIndex < 0) {
            return new ModuleFile(ApiPath.empty(), path);
        }
        final var apiPath = extractApiPath(path.substring(0, splitIndex));
        final var name = splitIndex == lastIndex ? "" : path.substring(splitIndex + 1);
        return new ModuleFile(apiPath, name);
    }

    private static ApiPath extractApiPath(final RequestParameters params) {
        return extractApiPath(params.pathParameters().childIdentifier());
    }

    private static ApiPath extractApiPath(final String path) {
        try {
            return ApiPath.parse(path);
        } catch (ParseException e) {
            throw new ServerErrorException(ErrorTag.BAD_ELEMENT,
                "API Path value '%s' is invalid. %s".formatted(path, e.getMessage()), e);
        }
    }

    private static <T extends ConsumableBody> T requestBody(final RequestParameters params,
            final Function<InputStream, T> jsonBodyBuilder, final Function<InputStream, T> xmlBodyBuilder) {
        return NettyMediaTypes.JSON_TYPES.contains(params.contentType())
            ? jsonBodyBuilder.apply(params.requestBody()) : xmlBodyBuilder.apply(params.requestBody());
    }

    private static FullHttpResponse optionsResponse(final RequestParameters params, final String allowHeaderValue) {
        final var response = new DefaultFullHttpResponse(params.protocolVersion(), HttpResponseStatus.OK,
            Unpooled.EMPTY_BUFFER);
        response.headers().set(HttpHeaderNames.ALLOW, allowHeaderValue);
        return response;
    }

    private record ModuleFile(ApiPath mountPath, String name) {
    }
}
