/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
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
import java.sql.PreparedStatement;
import java.text.ParseException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RestconfRequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequestDispatcher.class);

    private static final @NonNull CompletedRequest METHOD_NOT_ALLOWED_DATASTORE =
        new DefaultCompletedRequest(HttpResponseStatus.METHOD_NOT_ALLOWED, AbstractPendingOptions.HEADERS_DATASTORE);
    private static final @NonNull CompletedRequest METHOD_NOT_ALLOWED_READ_ONLY =
        new DefaultCompletedRequest(HttpResponseStatus.METHOD_NOT_ALLOWED, AbstractPendingOptions.HEADERS_READ_ONLY);
    private static final @NonNull CompletedRequest METHOD_NOT_ALLOWED_RPC =
        new DefaultCompletedRequest(HttpResponseStatus.METHOD_NOT_ALLOWED, AbstractPendingOptions.HEADERS_RPC);

    private static final @NonNull CompletedRequest NOT_FOUND = new DefaultCompletedRequest(HttpResponseStatus.NOT_FOUND);

    private static final @NonNull CompletedRequest NOT_ACCEPTABLE_DATA;
    private static final @NonNull CompletedRequest UNSUPPORTED_MEDIA_TYPE_DATA;
    private static final @NonNull CompletedRequest UNSUPPORTED_MEDIA_TYPE_PATCH;

    static {
        final var factory = DefaultHttpHeadersFactory.headersFactory().withCombiningHeaders(true);

        final var headers = factory.newEmptyHeaders().set(HttpHeaderNames.ACCEPT, List.of(
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            MediaTypes.APPLICATION_YANG_DATA_XML,
            // FIXME: do not advertize these types
            HttpHeaderValues.APPLICATION_JSON.toString(),
            HttpHeaderValues.APPLICATION_XML.toString(),
            NettyMediaTypes.TEXT_XML.toString()));

        NOT_ACCEPTABLE_DATA = new DefaultCompletedRequest(HttpResponseStatus.NOT_ACCEPTABLE, headers);
        UNSUPPORTED_MEDIA_TYPE_DATA = new DefaultCompletedRequest(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, headers);
        UNSUPPORTED_MEDIA_TYPE_PATCH = new DefaultCompletedRequest(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE,
            factory.newEmptyHeaders().set(HttpHeaderNames.ACCEPT, List.of(
                MediaTypes.APPLICATION_YANG_PATCH_JSON,
                MediaTypes.APPLICATION_YANG_PATCH_XML)));
    }

    @VisibleForTesting
    static final String MISSING_FILENAME_ERROR = "Module name is missing";

    private final @NonNull EndpointInvariants invariants;
    private final @NonNull PrincipalService principalService;

    private final String firstSegment;
    private final List<String> otherSegments;

    RestconfRequestDispatcher(final RestconfServer server, final PrincipalService principalService,
            final List<String> segments, final String restconfPath, final ErrorTagMapping errorTagMapping,
            final MessageEncoding defaultEncoding, final PrettyPrintParam defaultPrettyPrint) {
        invariants = new EndpointInvariants(server, defaultPrettyPrint, errorTagMapping, defaultEncoding,
            URI.create(requireNonNull(restconfPath)));
        this.principalService = requireNonNull(principalService);

        firstSegment = segments.getFirst();
        otherSegments = segments.stream().skip(1).collect(Collectors.toUnmodifiableList());

        LOG.info("{} initialized with service {}", getClass().getSimpleName(), server.getClass());
        LOG.info("Base path: {}, default accept: {}, default pretty print: {}", restconfPath,
            defaultEncoding.dataMediaType(), defaultPrettyPrint.value());
    }

    String firstSegment() {
        return firstSegment;
    }

    void dispatch(final @NonNull ImplementedMethod method, final URI targetUri, final SegmentPeeler peeler,
            final FullHttpRequest request, final RestconfRequest callback) {
        final var version = request.protocolVersion();
        // FIXME: this is here just because of test structure
        final var principal = principalService.acquirePrincipal(request);

        switch (prepare(method, targetUri, peeler, request)) {
            case CompletedRequest completed -> callback.onSuccess(completed.toHttpResponse(version));
            case PendingRequest<?> pending -> {
                LOG.debug("Dispatching {} {}", request.method(), targetUri);

                final var content = request.content();
                pending.execute(new RequestCompleter() {
                    @Override
                    public void requestFailed(final PendingRequest<?> request, final Exception cause) {
                        // FIXME: use this facility
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void requestComplete(final PendingRequest<?> request, final Response reply) {
                        final FullHttpResponse response;
                        switch (reply) {
                            case CharSourceResponse charSource -> {
                                final var content = Unpooled.buffer();
                                try {
                                    charSource.source().asByteSource(StandardCharsets.UTF_8)
                                        .copyTo(new ByteBufOutputStream(content));
                                } catch (IOException e) {
                                    requestFailed(request, e);
                                    return;
                                }

                                response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK, content);
                                response.headers()
                                    .set(HttpHeaderNames.CONTENT_TYPE, charSource.mediaType())
                                    .set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
                            }
                            case CompletedRequest completed -> {
                                response = completed.toHttpResponse(version);
                            }
                            case FormattableDataResponse formattable -> {
                                // FIXME: implement this
                                throw new UnsupportedOperationException();
                            }
                        }
                        callback.onSuccess(response);
                    }
                }, principal, content.readableBytes() == 0 ? InputStream.nullInputStream()
                    : new ByteBufInputStream(content));
            }
        }
    }

    /**
     * Prepare to service a request, by binding the request HTTP method and the request path to a resource and
     * validating request headers in that context. This method is required to not block.
     *
     * @param peeler the {@link SegmentPeeler} holding the unprocessed part of the request path
     * @param request the request itself
     * @return A {@link PreparedStatement}
     */
    @NonNull PreparedRequest prepare(final ImplementedMethod method, final URI targetUri, final SegmentPeeler peeler,
            final HttpRequest request) {
        LOG.debug("Preparing {} {}", method, request.uri());

        // peel all other segments out
        for (var segment : otherSegments) {
            if (!peeler.hasNext() || !segment.equals(peeler.next())) {
                return NOT_FOUND;
            }
        }

        if (!peeler.hasNext()) {
            // FIXME: we are rejecting requests to '{+restconf}', which matches JAX-RS server behaviour, but is not
            //        correct: we should be reporting the entire API Resource, as described in
            //        https://www.rfc-editor.org/rfc/rfc8040#section-3.3
            return NOT_FOUND;
        }

        final var headers = request.headers();
        return switch (peeler.next()) {
            case "data" -> prepareData(method, targetUri, peeler, headers);
            case "operations" -> prepareOperations(method, targetUri, peeler, headers);
            case "yang-library-version" -> prepareYangLibraryVersion(method, targetUri, peeler, headers);
            case "modules" -> prepareModules(method, targetUri, peeler, headers);
            default -> NOT_FOUND;
        };
    }

    /**
     * Process a request to <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.1">RFC 8040 {+restconf}/data</a>
     * resource.
     */
    private @NonNull PreparedRequest prepareData(final ImplementedMethod method, final URI targetUri,
            final SegmentPeeler peeler, final HttpHeaders headers) {
        return switch (method) {
            // delete target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.7
            case DELETE -> prepareWithApiPath(peeler, apiPath -> apiPath.isEmpty() ? METHOD_NOT_ALLOWED_DATASTORE
                : new PendingDataDelete(invariants, targetUri, apiPath));
            // retrieve data and metadata for a resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.3
            case GET -> prepareDataGet(targetUri, peeler, headers, true);
            // HEAD is same as GET but without content -> https://www.rfc-editor.org/rfc/rfc8040#section-4.2
            case HEAD -> prepareDataGet(targetUri, peeler, headers, false);
            // resource options -> https://www.rfc-editor.org/rfc/rfc8040#section-4.1
            case OPTIONS -> prepareWithApiPath(peeler,
                apiPath -> new PendingDataOptions(invariants, targetUri, apiPath));
            // PATCH -> https://www.rfc-editor.org/rfc/rfc8040#section-4.6
            case PATCH -> prepareDataPatch(targetUri, peeler, headers);
            // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
            // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
            case POST -> prepareDataPost(targetUri, peeler, headers);
            // create or replace target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.5
            case PUT -> prepareDataPut(targetUri, peeler, headers);
        };
    }

    // Common handling for both GET and HEAD methods
    private @NonNull PreparedRequest prepareDataGet(final URI targetUri, final SegmentPeeler peeler,
            final HttpHeaders headers, final boolean withContent) {
        // Attempt to choose an encoding based on user's preference. If we cannot pick one, responding with a 406 status
        // and list the encodings we support
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? NOT_ACCEPTABLE_DATA : prepareWithApiPath(peeler,
            apiPath -> new PendingDataGet(invariants, targetUri, encoding, apiPath, withContent));
    }

    private @NonNull PreparedRequest prepareDataPatch(final URI targetUri, final SegmentPeeler peeler,
            final HttpHeaders headers) {
        final var contentTypeValue = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeValue == null) {
            return DefaultCompletedRequest.badRequest(HttpHeaderNames.CONTENT_TYPE);
        }
        final var contentType = AsciiString.of(contentTypeValue);

        for (var encoding : MessageEncoding.values()) {
            // FIXME: tighten this check to just dataMediaType
            if (encoding.producesDataCompatibleWith(contentType)) {
                // Plain RESTCONF patch = merge target resource content ->
                // https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1
                return prepareWithApiPath(peeler,
                    apiPath -> new PendingDataPatchPlain(invariants, targetUri, encoding, apiPath));
            }
            if (encoding.patchMediaType().equals(contentType)) {
                // YANG Patch = ordered list of edits that are applied to the target datastore ->
                // https://www.rfc-editor.org/rfc/rfc8072#section-2
                return prepareWithApiPath(peeler,
                    apiPath -> new PendingDataPatchYang(invariants, targetUri, encoding, apiPath));
            }
        }

        return UNSUPPORTED_MEDIA_TYPE_PATCH;
    }

    // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
    // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
    private @NonNull PreparedRequest prepareDataPost(final URI targetUri, final SegmentPeeler peeler,
            final HttpHeaders headers) {
        final var contentTypeValue = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeValue == null) {
            return DefaultCompletedRequest.badRequest(HttpHeaderNames.CONTENT_TYPE);
        }

        final var encoding = chooseInputEncoding(contentTypeValue);
        return encoding == null ? UNSUPPORTED_MEDIA_TYPE_DATA : prepareWithApiPath(peeler,
            apiPath -> apiPath.isEmpty() ? new PendingDataCreate(invariants, targetUri, encoding)
                : new PendingDataPost(invariants, targetUri, encoding, apiPath));
    }

    private @NonNull PreparedRequest prepareDataPut(final URI targetUri, final SegmentPeeler peeler,
            final HttpHeaders headers) {
        final var contentTypeValue = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeValue == null) {
            return DefaultCompletedRequest.badRequest(HttpHeaderNames.CONTENT_TYPE);
        }

        final var encoding = chooseInputEncoding(contentTypeValue);
        return encoding == null ? UNSUPPORTED_MEDIA_TYPE_DATA
            : prepareWithApiPath(peeler, apiPath -> new PendingDataPut(invariants, targetUri, encoding, apiPath));
    }

    /**
     * Prepare a request to
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC 8040 {+restconf}/operations</a> resource.
     */
    @NonNullByDefault
    private PreparedRequest prepareOperations(final ImplementedMethod method, final URI targetUri,
            final SegmentPeeler peeler, final HttpHeaders headers) {
        return switch (method) {
            case GET -> prepareOperationsGet(targetUri, peeler, headers, true);
            case HEAD -> prepareOperationsGet(targetUri, peeler, headers, false);
            case OPTIONS -> prepareWithApiPath(peeler, apiPath -> apiPath.isEmpty()
                ? AbstractPendingOptions.READ_ONLY : new PendingOperationsOptions(invariants, targetUri, apiPath));
            case POST -> prepareOperationsPost(peeler, headers);
            default -> prepareWithApiPath(peeler, apiPath -> apiPath.isEmpty() ? METHOD_NOT_ALLOWED_READ_ONLY
                // TODO: This is incomplete. We are always reporting 405 Method Not Allowed, but we can do better.
                //       We should fire off an OPTIONS request for the apiPath and see if it exists: if it does not,
                //       we should report a 404 Not Found instead.
                : METHOD_NOT_ALLOWED_RPC);
        };
    }

    private @NonNull PreparedRequest prepareOperationsGet(final URI targetUri, final SegmentPeeler peeler,
            final HttpHeaders headers, final boolean withContent) {
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? NOT_ACCEPTABLE_DATA : prepareWithApiPath(peeler,
            apiPath -> new PendingOperationsGet(invariants, targetUri, encoding, apiPath, withContent));
    }

    private @NonNull PreparedRequest prepareOperationsPost(final SegmentPeeler peeler, final HttpHeaders headers) {

        // FIXME: implement this

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
    private @NonNull PreparedRequest prepareYangLibraryVersion(final ImplementedMethod method, final URI targetUri,
            final SegmentPeeler peeler, final HttpHeaders headers) {
        return peeler.remaining().isEmpty() ? switch (method) {
            case GET -> prepareYangLibraryVersion(targetUri, headers, true);
            case HEAD -> prepareYangLibraryVersion(targetUri, headers, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        } : NOT_FOUND;
    }

    private @NonNull PreparedRequest prepareYangLibraryVersion(final URI targetUri, final HttpHeaders headers,
            final boolean withContent) {
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? UNSUPPORTED_MEDIA_TYPE_DATA
            : new PendingYangLibraryVersionGet(invariants, targetUri, encoding, withContent);
    }

    /**
     * Access to YANG modules.
     */
    private @NonNull PreparedRequest prepareModules(final ImplementedMethod method, final URI targetUri,
            final SegmentPeeler peeler, final HttpHeaders headers) {
        return switch (method) {
            case GET -> prepareModulesGet(targetUri, peeler, headers, true);
            case HEAD -> prepareModulesGet(targetUri, peeler, headers, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private @NonNull PreparedRequest prepareModulesGet(final URI targetUri, final SegmentPeeler peeler,
            final HttpHeaders headers, final boolean withContent) {
        final var remaining = peeler.remaining();
        if (remaining.isEmpty()) {
            return NOT_FOUND;
        }

        // optional mountPath followed by file name separated by slash
        final var path = remaining.substring(1);
        final var lastSlash = path.lastIndexOf('/');
        final ApiPath mountPath;
        final String fileName;
        if (lastSlash != -1) {
            final var mountString = path.substring(0, lastSlash);
            try {
                mountPath = ApiPath.parse(path);
            } catch (ParseException e) {
                return DefaultCompletedRequest.badRequest(mountString, e);
            }
            fileName = path.substring(lastSlash + 1);
        } else {
            mountPath = ApiPath.empty();
            fileName = path;
        }

        if (fileName.isEmpty()) {
            return NOT_FOUND;
        }

        // YIN if explicitly requested
        // YANG by default, incl accept any
        // FIXME: we should use client's preferences
        final var doYin = headers.contains(HttpHeaderNames.ACCEPT, NettyMediaTypes.APPLICATION_YIN_XML, true)
            && !headers.contains(HttpHeaderNames.ACCEPT, NettyMediaTypes.APPLICATION_YANG, true);
        final var decoded = QueryStringDecoder.decodeComponent(fileName);

        return doYin ? new PendingModulesGetYin(invariants, targetUri, mountPath, decoded)
            : new PendingModulesGetYang(invariants, targetUri, mountPath, decoded);
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
                return DefaultCompletedRequest.badRequest(path, e);
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
            return invariants.defaultEncoding();
        }

        for (var acceptValue : acceptValues) {
            final var encoding = matchEncoding(acceptValue);
            if (encoding != null) {
                return encoding;
            }
        }
        return null;
    }

//    private static AsciiString extractContentType(final FullHttpRequest request, final AsciiString defaultType) {
//        final var contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
//        if (contentType != null) {
//            return AsciiString.of(contentType);
//        }
//        // when request body is empty content-type value plays no role, and eligible to be absent,
//        // in this case apply default type to prevent unsupported media type error when checked subsequently
//        return request.content().readableBytes() == 0 ? defaultType : AsciiString.EMPTY_STRING;
//    }

    private static @Nullable MessageEncoding matchEncoding(final String acceptValue) {
        // FIXME: match media types
        throw new UnsupportedOperationException();
    }
}
