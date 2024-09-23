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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
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

    private final RestconfServer server;
    private final PrincipalService principalService;
    private final ErrorTagMapping errorTagMapping;
    private final MessageEncoding defaultEncoding;
    private final PrettyPrintParam defaultPrettyPrint;

    private final String firstSegment;
    private final List<String> otherSegments;

    // '/{+restconf}/', i.e. an absolute path conforming to RestconfServer's 'restconfURI'
    private final URI restconfPath;

    RestconfRequestDispatcher(final RestconfServer server, final PrincipalService principalService,
            final List<String> segments, final String restconfPath, final ErrorTagMapping errorTagMapping,
            final MessageEncoding defaultEncoding, final PrettyPrintParam defaultPrettyPrint) {
        this.server = requireNonNull(server);
        this.principalService = requireNonNull(principalService);
        this.restconfPath = URI.create(requireNonNull(restconfPath));
        this.errorTagMapping = requireNonNull(errorTagMapping);
        this.defaultEncoding = requireNonNull(defaultEncoding);
        this.defaultPrettyPrint = requireNonNull(defaultPrettyPrint);

        firstSegment = segments.getFirst();
        otherSegments = segments.stream().skip(1).collect(Collectors.toUnmodifiableList());

        LOG.info("{} initialized with service {}", getClass().getSimpleName(), server.getClass());
        LOG.info("Base path: {}, default accept: {}, default pretty print: {}", restconfPath,
            defaultEncoding.dataMediaType(), defaultPrettyPrint.value());
    }

    String firstSegment() {
        return firstSegment;
    }

    @SuppressWarnings("IllegalCatch")
    void dispatch(final RestconfSession session, final @NonNull ImplementedMethod method, final URI targetUri,
            final SegmentPeeler peeler, final FullHttpRequest request, final RestconfRequest callback) {
        LOG.debug("Dispatching {} {}", method, targetUri);

        // FIXME: this is here just because of test structure
        final var principal = principalService.acquirePrincipal(request);

        // peel all other segments out
        for (var segment : otherSegments) {
            if (!peeler.hasNext() || !segment.equals(peeler.next())) {
                callback.onSuccess(notFound(request));
                return;
            }
        }

        if (!peeler.hasNext()) {
            // FIXME: we are rejecting requests to '{+restconf}', which matches JAX-RS server behaviour, but is not
            //        correct: we should be reporting the entire API Resource, as described in
            //        https://www.rfc-editor.org/rfc/rfc8040#section-3.3
            callback.onSuccess(notFound(request));
            return;
        }

        final var segment = peeler.next();
        final var rawPath = peeler.remaining();
        final var rawQuery = targetUri.getRawQuery();
        final var decoder = new QueryStringDecoder(rawQuery != null ? rawPath + "?" + rawQuery : rawPath);
        final var params = new RequestParameters(method, targetUri.resolve(restconfPath), decoder, request, principal,
            errorTagMapping, defaultEncoding, defaultPrettyPrint);

        try {
            switch (segment) {
                case "data" -> processDataRequest(session, params, callback);
                case "operations" -> processOperationsRequest(session, params, callback);
                case "yang-library-version" -> processYangLibraryVersion(session, params, callback);
                case "modules" -> processModules(session, params, callback);
                default -> callback.onSuccess(method == ImplementedMethod.OPTIONS
                    ? optionsResponse(params, ImplementedMethod.OPTIONS.toString()) : notFound(request));
            }
        } catch (RuntimeException e) {
            LOG.error("Error processing request {} {}", method, request.uri(), e);
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
    private void processDataRequest(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback) {
        final var contentType = params.contentType();
        final var apiPath = extractApiPath(params);
        switch (params.method()) {
            // resource options -> https://www.rfc-editor.org/rfc/rfc8040#section-4.1
            case OPTIONS -> {
                final var request = new OptionsServerRequest(session, params, callback);
                if (apiPath.isEmpty()) {
                    server.dataOPTIONS(request);
                } else {
                    server.dataOPTIONS(request, apiPath);
                }
            }
            // retrieve data and metadata for a resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.3
            // HEAD is same as GET but without content -> https://www.rfc-editor.org/rfc/rfc8040#section-4.2
            case HEAD, GET -> getData(session, params, callback, apiPath);
            case POST -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
                    // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
                    postData(session, params, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            case PUT -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // create or replace target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.5
                    putData(session, params, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            case PATCH -> {
                if (RESTCONF_TYPES.contains(contentType)) {
                    // Plain RESTCONF patch = merge target resource content ->
                    // https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1
                    patchData(session, params, callback, apiPath);
                } else if (YANG_PATCH_TYPES.contains(contentType)) {
                    // YANG Patch = ordered list of edits that are applied to the target datastore ->
                    // https://www.rfc-editor.org/rfc/rfc8072#section-2
                    yangPatchData(session, params, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            // delete target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.7
            case DELETE -> deleteData(session, params, callback, apiPath);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private void getData(final RestconfSession session, final RequestParameters params, final RestconfRequest callback,
            final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataGetResult>(session, params, callback) {
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

    private void postData(final RestconfSession session, final RequestParameters params, final RestconfRequest callback,
            final ApiPath apiPath) {
        if (apiPath.isEmpty()) {
            server.dataPOST(postRequest(session, params, callback),
                requestBody(params, JsonChildBody::new, XmlChildBody::new));
        } else {
            server.dataPOST(postRequest(session, params, callback), apiPath,
                requestBody(params, JsonDataPostBody::new, XmlDataPostBody::new));
        }
    }

    private static <T extends DataPostResult> ServerRequest<T> postRequest(final RestconfSession session,
            final RequestParameters params, final RestconfRequest callback) {
        return new NettyServerRequest<>(session, params, callback) {
            @Override
            FullHttpResponse transform(final DataPostResult result) {
                return switch (result) {
                    case CreateResourceResult createResult -> {
                        yield responseBuilder(requestParams, HttpResponseStatus.CREATED)
                            .setHeader(HttpHeaderNames.LOCATION,
                                requestParams.restconfURI() + "data/" + createResult.createdPath())
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

    private void putData(final RestconfSession session, final RequestParameters params, final RestconfRequest callback,
            final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataPutResult>(session, params, callback) {
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

    private void patchData(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataPatchResult>(session, params, callback) {
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

    private void yangPatchData(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback, final ApiPath apiPath) {
        final var request = new NettyServerRequest<DataYangPatchResult>(session, params, callback) {
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

    private void deleteData(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback, final ApiPath apiPath) {
        server.dataDELETE(new NettyServerRequest<>(session, params, callback) {
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
    private void processOperationsRequest(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback) {
        final var apiPath = extractApiPath(params);
        switch (params.method()) {
            case OPTIONS -> {
                if (apiPath.isEmpty()) {
                    callback.onSuccess(OptionsServerRequest.withoutPatch(params.protocolVersion(),
                        "GET, HEAD, OPTIONS"));
                } else {
                    server.operationsOPTIONS(new OptionsServerRequest(session, params, callback), apiPath);
                }
            }
            case HEAD, GET -> getOperations(session, params, callback, apiPath);
            case POST -> {
                if (NettyMediaTypes.RESTCONF_TYPES.contains(params.contentType())) {
                    // invoke rpc -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2
                    postOperations(session, params, callback, apiPath);
                } else {
                    callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
                }
            }
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private void getOperations(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback, final ApiPath apiPath) {
        final var request = new FormattableServerRequest(session, params, callback);
        if (apiPath.isEmpty()) {
            server.operationsGET(request);
        } else {
            server.operationsGET(request, apiPath);
        }
    }

    private void postOperations(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback, final ApiPath apiPath) {
        server.operationsPOST(new NettyServerRequest<>(session, params, callback) {
            @Override
            FullHttpResponse transform(final InvokeResult result) {
                final var output = result.output();
                return output == null ? simpleResponse(requestParams, HttpResponseStatus.NO_CONTENT)
                    : responseBuilder(requestParams, HttpResponseStatus.OK).setBody(output).build();
            }
        }, params.restconfURI(), apiPath,
            requestBody(params, JsonOperationInputBody::new, XmlOperationInputBody::new));
    }

    /**
     * Process a request to
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">{+restconf}/yang-library-version</a> resource.
     */
    private void processYangLibraryVersion(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback) {
        switch (params.method()) {
            case OPTIONS -> callback.onSuccess(optionsResponse(params, "GET, HEAD, OPTIONS"));
            case HEAD, GET -> server.yangLibraryVersionGET(new FormattableServerRequest(session, params, callback));
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    /**
     * Access to YANG modules.
     */
    private void processModules(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback) {
        switch (params.method()) {
            case OPTIONS -> callback.onSuccess(optionsResponse(params, "GET, HEAD, OPTIONS"));
            case HEAD, GET -> getModule(session, params, callback);
            default -> callback.onSuccess(unmappedRequestErrorResponse(params));
        }
    }

    private void getModule(final RestconfSession session, final RequestParameters params,
            final RestconfRequest callback) {
        final var rawPath = params.remainingRawPath();
        if (rawPath.isEmpty()) {
            callback.onSuccess(simpleErrorResponse(params, ErrorTag.MISSING_ELEMENT, MISSING_FILENAME_ERROR));
            return;
        }

        final var file = extractModuleFile(rawPath.substring(1));
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
            final var request = getModuleRequest(session, params, callback, NettyMediaTypes.APPLICATION_YIN_XML);
            if (file.mountPath.isEmpty()) {
                server.modulesYinGET(request, file.name(), revision);
            } else {
                server.modulesYinGET(request, file.mountPath(), file.name(), revision);
            }
        } else {
            // YANG by default, incl accept any
            final var request = getModuleRequest(session, params, callback, NettyMediaTypes.APPLICATION_YANG);
            if (file.mountPath.isEmpty()) {
                server.modulesYangGET(request, file.name(), revision);
            } else {
                server.modulesYangGET(request, file.mountPath(), file.name(), revision);
            }
        }
    }

    private static ServerRequest<ModulesGetResult> getModuleRequest(final RestconfSession session,
            final RequestParameters params, final RestconfRequest callback, final AsciiString mediaType) {
        return new NettyServerRequest<>(session, params, callback) {
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
            return new ModuleFile(ApiPath.empty(), QueryStringDecoder.decodeComponent(path));
        }
        final var apiPath = extractApiPath(path.substring(0, splitIndex));
        final var name = splitIndex == lastIndex ? "" : path.substring(splitIndex + 1);
        return new ModuleFile(apiPath, QueryStringDecoder.decodeComponent(name));
    }

    private static ApiPath extractApiPath(final RequestParameters params) {
        final var str = params.remainingRawPath();
        return str.isEmpty() ? ApiPath.empty() : extractApiPath(str.substring(1));
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
