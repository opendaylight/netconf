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
import com.google.common.base.VerifyException;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.PreparedStatement;
import java.text.ParseException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
import org.opendaylight.restconf.server.api.OptionsResult;
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

    private static final @NonNull PreparedRequest NOT_ACCEPTABLE_DATA =
        // FIXME: list acceptable media types
        ImmediateResponse.notAcceptable(null);

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
    void dispatch(final URI targetUri, final SegmentPeeler peeler, final FullHttpRequest request,
            final RestconfRequest callback) {
        // FIXME: this is here just because of test structure
        final var principal = principalService.acquirePrincipal(request);

        switch (prepare(principal, targetUri, peeler, request)) {
            case ImmediateResponse immediate -> callback.onSuccess(immediate.toHttpResponse(request.protocolVersion()));
            case RequestHandle handle -> {
                LOG.debug("Dispatching {} {}", request.method(), targetUri);


            }
        }
    }

    /**
     * Prepare to service a request. Preparation here includes all activities which can be done without having access
     * and without blocking.
     *
     * @param targetUri resolved Target URI
     * @param peeler the {@link SegmentPeeler} holding the unprocessed part of the request path
     * @param request the request itself
     * @return A {@link PreparedStatement}
     */
    @NonNull PreparedRequest prepare(final Principal principal, final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request) {
        LOG.debug("Preparing {} {}", request.method(), targetUri);

        // peel all other segments out
        for (var segment : otherSegments) {
            if (!peeler.hasNext() || !segment.equals(peeler.next())) {
                return ImmediateResponse.notFound();
            }
        }

        if (!peeler.hasNext()) {
            // FIXME: we are rejecting requests to '{+restconf}', which matches JAX-RS server behaviour, but is not
            //        correct: we should be reporting the entire API Resource, as described in
            //        https://www.rfc-editor.org/rfc/rfc8040#section-3.3
            return ImmediateResponse.notFound();
        }

        return switch (peeler.next()) {
            case "data" -> prepareData(principal, targetUri, peeler, request);
            case "operations" -> prepareOperations(principal, targetUri, peeler, request);
            case "yang-library-version" -> prepareYangLibraryVersion(principal, targetUri, peeler, request);
            case "modules" -> prepareModules(principal, targetUri, peeler, request);
            default -> ImmediateResponse.notFound();
        };
    }

    private @NonNull PreparedRequest prepareData(final Principal principal, final URI targetUri,
            final SegmentPeeler peeler, final HttpRequest request) {
        return switch (request.method().name()) {
            // delete target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.7
            case "DELETE" -> prepareDataDELETE(principal, peeler);
            // retrieve data and metadata for a resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.3
            case "GET" -> prepareDataGET(principal, targetUri, peeler, request, true);
            // HEAD is same as GET but without content -> https://www.rfc-editor.org/rfc/rfc8040#section-4.2
            case "HEAD" -> prepareDataGET(principal, targetUri, peeler, request, false);
            // resource options -> https://www.rfc-editor.org/rfc/rfc8040#section-4.1
            case "OPTIONS" -> prepareWithApiPath(peeler, DataOptionsRequest::new);
            // PATCH -> https://www.rfc-editor.org/rfc/rfc8040#section-4.6
            case "PATCH" -> prepareDataPATCH(principal, targetUri, peeler, request);
            // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
            // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
            case "POST" -> prepareDataPOST(principal, targetUri, peeler, request);
            // create or replace target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.5
            case "PUT" -> prepareDataPUT(principal, targetUri, peeler, request);
            default -> throw new VerifyException("Should never be reached");
        };
    }

    private static @NonNull PreparedRequest prepareDataDELETE(final Principal principal, final SegmentPeeler peeler) {
        return prepareWithApiPath(peeler, apiPath -> apiPath.isEmpty()
            ? ImmediateResponse.methodNotAllowed(OptionsResult.DATASTORE) : new DataDeleteRequest(apiPath));
    }

    // Commom handling for both GET and HEAD methods
    private @NonNull PreparedRequest prepareDataGET(final Principal principal, final URI targetUri,
            final SegmentPeeler peeler, final HttpRequest request, final boolean withContent) {
        // Attempt to choose an encoding based on user's preference. If we cannot pick one, responding with a 406 status
        // and list the encodings we support
        final var encoding = chooseEncoding(request.headers());
        return encoding == null ? NOT_ACCEPTABLE_DATA
            : prepareWithApiPath(peeler, apiPath -> new DataGetRequest(encoding, apiPath, withContent));
    }

    private @NonNull PreparedRequest prepareDataPATCH(final Principal principal, final URI targetUri,
            final SegmentPeeler peeler, final HttpRequest request) {
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

    private @NonNull PreparedRequest prepareDataPOST(final Principal principal, final URI targetUri,
            final SegmentPeeler peeler, final HttpRequest request) {
        if (RESTCONF_TYPES.contains(contentType)) {
            // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
            // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
            postData(params, callback, apiPath);
        } else {
            callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
        }
    }

    private @NonNull PreparedRequest prepareDataPUT(final Principal principal, final URI targetUri,
            final SegmentPeeler peeler, final HttpRequest request) {
        if (RESTCONF_TYPES.contains(contentType)) {
            putData(params, callback, apiPath);
        } else {
            callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
        }
    }

    private @NonNull PreparedRequest prepareOperations(final Principal principal, final URI targetUri,
        final SegmentPeeler peeler, final HttpRequest request) {
        // TODO Auto-generated method stub
        return null;
    }

    private @NonNull PreparedRequest prepareYangLibraryVersion(final Principal principal, final URI targetUri,
            final SegmentPeeler peeler, final HttpRequest request) {
        // TODO Auto-generated method stub
        return null;
    }

    private @NonNull PreparedRequest prepareModules(final Principal principal, final URI targetUri,
            final SegmentPeeler peeler, final HttpRequest request) {
        // TODO Auto-generated method stub
        return null;
    }

    @NonNullByDefault
    private static PreparedRequest prepareWithApiPath(final SegmentPeeler peeler,
            final Function<ApiPath, PreparedRequest> function) {
        final var remaining = peeler.remaining();
        final ApiPath apiPath;
        if (!remaining.isEmpty()) {
            final var path = remaining.substring(1);
            try {
                apiPath = ApiPath.parse(path);
            } catch (ParseException e) {
                return ImmediateResponse.badRequest(path, e);
            }
        } else {
            apiPath = ApiPath.empty();
        }
        return function.apply(apiPath);
    }

    @Nullable MessageEncoding chooseEncoding(final HttpHeaders headers) {
        final var acceptValues = headers.getAll(HttpHeaderNames.ACCEPT);
        if (acceptValues.isEmpty()) {
            return defaultEncoding;
        }

        for (var acceptValue : acceptValues) {
            final var encoding = matchEncoding(acceptValue);
            if (encoding != null) {
                return encoding;
            }
        }
        return null;
    }

    private static @Nullable MessageEncoding matchEncoding(final String acceptValue) {
        // FIXME: match media types
        throw new UnsupportedOperationException();
    }

//    private void xxx() {
//
//        final var rawPath = peeler.remaining();
//        final var rawQuery = targetUri.getRawQuery();
//        final var decoder = new QueryStringDecoder(rawQuery != null ? rawPath + "?" + rawQuery : rawPath);
//        final var params = new RequestParameters(targetUri.resolve(restconfPath), decoder, request, principal,
//            errorTagMapping, defaultAcceptType, defaultPrettyPrint);
//
//        try {
//            switch (segment) {
//                case "data" -> processDataRequest(params, callback);
//                case "operations" -> processOperationsRequest(params, callback);
//                case "yang-library-version" -> processYangLibraryVersion(params, callback);
//                case "modules" -> processModules(params, callback);
//                default -> callback.onSuccess(HttpMethod.OPTIONS.equals(params.method())
//                    ? optionsResponse(params, HttpMethod.OPTIONS.name()) : notFound(request));
//            }
//        } catch (RuntimeException e) {
//            LOG.error("Error processing request {} {}", request.method(), request.uri(), e);
//            final var errorTag = e instanceof ServerErrorException see ? see.errorTag() : ErrorTag.OPERATION_FAILED;
//            callback.onSuccess(simpleErrorResponse(params, errorTag, e.getMessage()));
//        }
//    }

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
        }, params.restconfURI(), apiPath,
            requestBody(params, JsonOperationInputBody::new, XmlOperationInputBody::new));
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
