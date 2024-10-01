/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.ResponseUtils.simpleErrorResponse;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;
import static org.opendaylight.restconf.server.ResponseUtils.unmappedRequestErrorResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.VerifyException;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OptionsResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
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

    private static final @NonNull CompletedRequest NOT_ACCEPTABLE_DATA =
        // FIXME: list acceptable media types
        CompletedRequest.notAcceptable(null);
    private static final @NonNull CompletedRequest UNSUPPORTED_MEDIA_TYPE_DATA =
        // FIXME: list acceptable media types
        CompletedRequest.unsupportedMediaType(null);
    private static final @NonNull CompletedRequest UNSUPPORTED_MEDIA_TYPE_PATCH =
        // FIXME: list acceptable media types
        CompletedRequest.unsupportedMediaType(null);

    private static final @NonNull CompletedRequest GET_HEAD_OPTIONS;

    static {
        final var headers = DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders();
        headers.set(HttpHeaderNames.ACCEPT, "GET, HEAD, OPTIONS");
        GET_HEAD_OPTIONS = new CompletedRequest(HttpResponseStatus.OK, headers);
    }

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

        switch (prepare(targetUri, peeler, request)) {
            case CompletedRequest completed -> callback.onSuccess(completed.toHttpResponse(request.protocolVersion()));
            case PendingRequest pending -> {
                LOG.debug("Dispatching {} {}", request.method(), targetUri);

                // final var rawPath = peeler.remaining();
                // final var rawQuery = targetUri.getRawQuery();
                // final var decoder = new QueryStringDecoder(rawQuery != null ? rawPath + "?" + rawQuery : rawPath);
                // final var params = new RequestParameters(targetUri.resolve(restconfPath), decoder, request, principal,
                //     errorTagMapping, defaultAcceptType, defaultPrettyPrint);
                //
                // try {
                //     switch (segment) {
                //         case "data" -> processDataRequest(params, callback);
                //         case "operations" -> processOperationsRequest(params, callback);
                //         case "yang-library-version" -> processYangLibraryVersion(params, callback);
                //         case "modules" -> processModules(params, callback);
                //         default -> callback.onSuccess(HttpMethod.OPTIONS.equals(params.method())
                //             ? optionsResponse(params, HttpMethod.OPTIONS.name()) : notFound(request));
                //     }
                // } catch (RuntimeException e) {
                //     LOG.error("Error processing request {} {}", request.method(), request.uri(), e);
                //     final var errorTag = e instanceof ServerErrorException see ? see.errorTag()
                //         : ErrorTag.OPERATION_FAILED;
                //     callback.onSuccess(simpleErrorResponse(params, errorTag, e.getMessage()));
                // }
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
    @NonNull PreparedRequest prepare(final URI targetUri, final SegmentPeeler peeler, final HttpRequest request) {
        LOG.debug("Preparing {} {}", request.method(), targetUri);

        // peel all other segments out
        for (var segment : otherSegments) {
            if (!peeler.hasNext() || !segment.equals(peeler.next())) {
                return CompletedRequest.notFound();
            }
        }

        if (!peeler.hasNext()) {
            // FIXME: we are rejecting requests to '{+restconf}', which matches JAX-RS server behaviour, but is not
            //        correct: we should be reporting the entire API Resource, as described in
            //        https://www.rfc-editor.org/rfc/rfc8040#section-3.3
            return CompletedRequest.notFound();
        }

        return switch (peeler.next()) {
            case "data" -> prepareData(targetUri, peeler, request);
            case "operations" -> prepareOperations(targetUri, peeler, request);
            case "yang-library-version" -> prepareYangLibraryVersion(targetUri, peeler, request);
            case "modules" -> prepareModules(targetUri, peeler, request);
            default -> CompletedRequest.notFound();
        };
    }

    /**
     * Process a request to <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.1">RFC 8040 {+restconf}/data</a>
     * resource.
     */
    private @NonNull PreparedRequest prepareData(final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request) {
        return switch (request.method().name()) {
            // delete target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.7
            case "DELETE" -> prepareDataDelete(peeler);
            // retrieve data and metadata for a resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.3
            case "GET" -> prepareDataGet(targetUri, peeler, request, true);
            // HEAD is same as GET but without content -> https://www.rfc-editor.org/rfc/rfc8040#section-4.2
            case "HEAD" -> prepareDataGet(targetUri, peeler, request, false);
            // resource options -> https://www.rfc-editor.org/rfc/rfc8040#section-4.1
            case "OPTIONS" -> prepareWithApiPath(peeler, PendingDataOptions::new);
            // PATCH -> https://www.rfc-editor.org/rfc/rfc8040#section-4.6
            case "PATCH" -> prepareDataPatch(targetUri, peeler, request);
            // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
            // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
            case "POST" -> prepareDataPost(targetUri, peeler, request);
            // create or replace target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.5
            case "PUT" -> prepareDataPut(targetUri, peeler, request);
            default -> throw new VerifyException("Should never be reached");
        };
    }

    private static @NonNull PreparedRequest prepareDataDelete(final SegmentPeeler peeler) {
        return prepareWithApiPath(peeler, apiPath -> apiPath.isEmpty()
            ? CompletedRequest.methodNotAllowed(OptionsResult.DATASTORE) : new PendingDataDelete(apiPath));
    }

    // Common handling for both GET and HEAD methods
    private @NonNull PreparedRequest prepareDataGet(final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request, final boolean withContent) {
        // Attempt to choose an encoding based on user's preference. If we cannot pick one, responding with a 406 status
        // and list the encodings we support
        final var encoding = chooseOutputEncoding(request.headers());
        return encoding == null ? NOT_ACCEPTABLE_DATA
            : prepareWithApiPath(peeler, apiPath -> new PendingDataGet(encoding, apiPath, withContent));
    }

    private static @NonNull PreparedRequest prepareDataPatch(final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request) {
        final var contentTypeValue = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeValue == null) {
            return CompletedRequest.badRequest(HttpHeaderNames.CONTENT_TYPE);
        }
        final var contentType = AsciiString.of(contentTypeValue);

        for (var encoding : MessageEncoding.values()) {
            // FIXME: tighten this check to just dataMediaType
            if (encoding.producesDataCompatibleWith(contentType)) {
                // Plain RESTCONF patch = merge target resource content ->
                // https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1
                return prepareWithApiPath(peeler, apiPath -> new PendingDataPatchPlain(encoding, apiPath));
            }
            if (encoding.patchMediaType().equals(contentType)) {
                // YANG Patch = ordered list of edits that are applied to the target datastore ->
                // https://www.rfc-editor.org/rfc/rfc8072#section-2
                return prepareWithApiPath(peeler, apiPath -> new PendingDataPatchYang(encoding, apiPath));
            }
        }

        return UNSUPPORTED_MEDIA_TYPE_PATCH;
    }

    // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
    // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
    private static @NonNull PreparedRequest prepareDataPost(final URI targetUri,
            final SegmentPeeler peeler, final HttpRequest request) {
        final var contentTypeValue = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeValue == null) {
            return CompletedRequest.badRequest(HttpHeaderNames.CONTENT_TYPE);
        }

        final var encoding = chooseInputEncoding(contentTypeValue);
        return encoding == null ? UNSUPPORTED_MEDIA_TYPE_DATA
            : prepareWithApiPath(peeler, apiPath -> new PendingDataPost(encoding, apiPath));
    }

    private static @NonNull PreparedRequest prepareDataPut(final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request) {
        final var contentTypeValue = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeValue == null) {
            return CompletedRequest.badRequest(HttpHeaderNames.CONTENT_TYPE);
        }

        final var encoding = chooseInputEncoding(contentTypeValue);
        return encoding == null ? UNSUPPORTED_MEDIA_TYPE_DATA
            : prepareWithApiPath(peeler, apiPath -> new PendingDataPut(encoding, apiPath));
    }

    /**
     * Prepare a request to
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC 8040 {+restconf}/operations</a> resource.
     */
    private @NonNull PreparedRequest prepareOperations(final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request) {
        return switch (request.method().name()) {
            case "GET" -> prepareOperationsGet(peeler, request, true);
            case "HEAD" -> prepareOperationsGet(peeler, request, false);
            case "OPTIONS" -> prepareWithApiPath(peeler,
                apiPath -> apiPath.isEmpty() ? GET_HEAD_OPTIONS : new PendingOperationsOptions(apiPath));
            case "POST" -> prepareOperationsPost(targetUri, peeler, request);
            default -> prepareWithApiPath(peeler, apiPath -> apiPath.isEmpty()
                ? CompletedRequest.methodNotAllowed(OptionsResult.READ_ONLY)
                // TODO: This is incomplete. We are always reporting 405 Method Not Allowed, but we can do better.
                //       We should fire off an OPTIONS request for the apiPath and see if it exists: if it does not,
                //       we should report a 404 Not Found instead.
                : CompletedRequest.methodNotAllowed(OptionsResult.RPC));
        };
    }

    private @NonNull PreparedRequest prepareOperationsGet(final SegmentPeeler peeler, final HttpRequest request,
            final boolean withContent) {
        final var encoding = chooseOutputEncoding(request.headers());
        return encoding == null ? NOT_ACCEPTABLE_DATA
            : prepareWithApiPath(peeler, apiPath -> new PendingOperationsGet(encoding, apiPath, withContent));
    }

    private @NonNull PreparedRequest prepareOperationsPost(final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request) {

        //      if (NettyMediaTypes.RESTCONF_TYPES.contains(params.contentType())) {
        //      // invoke rpc -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2
        //      postOperations(params, callback, apiPath);
        //  } else {
        //      callback.onSuccess(unsupportedMediaTypeErrorResponse(params));
        //  }

        throw new UnsupportedOperationException();
    }

    /**
     * Prepare a request to
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">{+restconf}/yang-library-version</a> resource.
     */
    private @NonNull PreparedRequest prepareYangLibraryVersion(final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request) {
        return peeler.remaining().isEmpty() ? switch (request.method().name()) {
            case "GET" -> prepareYangLibraryVersion(request, true);
            case "HEAD" -> prepareYangLibraryVersion(request, false);
            case "OPTIONS" -> GET_HEAD_OPTIONS;
            default -> CompletedRequest.methodNotAllowed(OptionsResult.READ_ONLY);
        } : CompletedRequest.notFound();
    }

    private @NonNull PreparedRequest prepareYangLibraryVersion(final HttpRequest request, final boolean withContent) {
        final var encoding = chooseOutputEncoding(request.headers());
        return encoding == null ? UNSUPPORTED_MEDIA_TYPE_DATA : new PendingYangLibraryVersionGet(encoding, withContent);
    }

    private @NonNull PreparedRequest prepareModules(final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request) {
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
                return CompletedRequest.badRequest(path, e);
            }
        } else {
            apiPath = ApiPath.empty();
        }
        return function.apply(apiPath);
    }

    // FIXME: this is quite insufficient, as per https://www.rfc-editor.org/rfc/rfc9112#name-message-body
    //
    //    The presence of a message body in a request is signaled by a Content-Length or Transfer-Encoding header field.
    //    Request message framing is independent of method semantics.
    //
    private static @Nullable MessageEncoding chooseInputEncoding(final @NonNull String contentTypeValue) {
        final var contentType = AsciiString.of(contentTypeValue);

        for (var encoding : MessageEncoding.values()) {
            // FIXME: tighten this check to just dataMediaType
            if (encoding.producesDataCompatibleWith(contentType)) {
                return encoding;
            }
        }

        return null;
    }

    private @Nullable MessageEncoding chooseOutputEncoding(final HttpHeaders headers) {
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
