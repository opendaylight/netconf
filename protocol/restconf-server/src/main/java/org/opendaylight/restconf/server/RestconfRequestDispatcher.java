/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
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

    private static final @NonNull CompletedRequest NOT_FOUND =
        new DefaultCompletedRequest(HttpResponseStatus.NOT_FOUND);

    private static final @NonNull CompletedRequest NOT_ACCEPTABLE_DATA;
    private static final @NonNull CompletedRequest UNSUPPORTED_MEDIA_TYPE_DATA;
    private static final @NonNull CompletedRequest UNSUPPORTED_MEDIA_TYPE_PATCH;

    static {
        final var factory = DefaultHttpHeadersFactory.headersFactory();

        final var headers = factory.newEmptyHeaders().set(HttpHeaderNames.ACCEPT, String.join(", ", List.of(
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            MediaTypes.APPLICATION_YANG_DATA_XML,
            // FIXME: do not advertize these types
            HttpHeaderValues.APPLICATION_JSON.toString(),
            HttpHeaderValues.APPLICATION_XML.toString(),
            NettyMediaTypes.TEXT_XML.toString())));

        NOT_ACCEPTABLE_DATA = new DefaultCompletedRequest(HttpResponseStatus.NOT_ACCEPTABLE, headers);
        UNSUPPORTED_MEDIA_TYPE_DATA = new DefaultCompletedRequest(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, headers);
        UNSUPPORTED_MEDIA_TYPE_PATCH = new DefaultCompletedRequest(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE,
            factory.newEmptyHeaders().set(HttpHeaderNames.ACCEPT, AbstractPendingOptions.ACCEPTED_PATCH_MEDIA_TYPES));
    }

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

    @NonNullByDefault
    void dispatch(final SegmentPeeler peeler, final ImplementedMethod method, final URI targetUri,
            final FullHttpRequest request, final RestconfRequest callback) {
        final var version = request.protocolVersion();

        switch (prepare(peeler, method, targetUri, request.headers(), principalService.acquirePrincipal(request))) {
            case CompletedRequest completed -> callback.onSuccess(completed.toHttpResponse(version));
            case PendingRequest<?> pending -> {
                LOG.debug("Dispatching {} {}", request.method(), targetUri);

                final var content = request.content();
                pending.execute(new PendingRequestListener() {
                    @Override
                    public void requestFailed(final PendingRequest<?> request, final Exception cause) {
                        LOG.warn("Internal error while processing {}", request, cause);
                        final var response = new DefaultFullHttpResponse(version,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
                        final var content = response.content();
                        // Note: we are tempted to do a cause.toString() here, but we are dealing with unhandled badness
                        //       here, so we do not want to be too revealing -- hence a message is all the user gets.
                        ByteBufUtil.writeUtf8(content, cause.getMessage());
                        HttpUtil.setContentLength(response, content.readableBytes());
                        callback.onSuccess(response);
                    }

                    @Override
                    public void requestComplete(final PendingRequest<?> request, final Response reply) {
                        // FIXME: ServerRequests typically finish with a FormattableBody, which can contain a huge
                        //        entity, which we do *not* want to completely buffer to a FullHttpResponse.
                        final FullHttpResponse response;
                        switch (reply) {
                            case CompletedRequest completed -> {
                                response = completed.toHttpResponse(version);
                            }

                            // FIXME: these payloads use a synchronous dump of data into the socket. We cannot safely
                            //        do that on the event loop, because a slow client would end up throttling our IO
                            //        threads simply because of TCP window and similar queuing/backpressure things.
                            //
                            //        we really want to kick off a virtual thread to take care of that, i.e. doing its
                            //        own synchronous write thing, talking to a short queue (SPSC?) of HttpObjects.
                            //
                            //        the event loop of each channel would be the consumer of that queue, picking them
                            //        off as quickly as possible, but execting backpressure if the amount of pending
                            //        stuff goes up.
                            //
                            //        as for the HttpObjects: this effectively means that the OutputStreams used in the
                            //        below code should be replaced with entities which perform chunking:
                            //        - buffer initial stuff, so that we produce a FullHttpResponse if the payload is
                            //          below 256KiB (or so), i.e. producing Content-Length header and dumping the thing
                            //          in one go
                            //        - otherwise emit just HttpResponse with Transfer-Enconding: chunked and continue
                            //          sending out chunks (of reasonable size).
                            //        - finish up with a LastHttpContent

                            case CharSourceResponse charSource -> {
                                response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK);
                                final var content = response.content();
                                try (var os = new ByteBufOutputStream(content)) {
                                    charSource.source().asByteSource(StandardCharsets.UTF_8).copyTo(os);
                                } catch (IOException e) {
                                    requestFailed(request, e);
                                    return;
                                }

                                response.headers()
                                    .set(HttpHeaderNames.CONTENT_TYPE, charSource.mediaType())
                                    .set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
                            }
                            case FormattableDataResponse formattable -> {
                                response = new DefaultFullHttpResponse(version, formattable.status());
                                final var content = response.content();

                                try (var os = new ByteBufOutputStream(content)) {
                                    formattable.writeTo(os);
                                } catch (IOException e) {
                                    requestFailed(request, e);
                                    return;
                                }

                                final var headers = response.headers();
                                final var extra = formattable.headers();
                                if (extra != null) {
                                    headers.set(extra);
                                }
                                headers
                                    .set(HttpHeaderNames.CONTENT_TYPE, formattable.encoding().dataMediaType())
                                    .set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
                            }
                        }
                        callback.onSuccess(response);
                    }
                }, content.readableBytes() == 0 ? InputStream.nullInputStream() : new ByteBufInputStream(content));
            }
        }
    }

    /**
     * Prepare to service a request, by binding the request HTTP method and the request path to a resource and
     * validating request headers in that context. This method is required to not block.
     *
     * @param peeler the {@link SegmentPeeler} holding the unprocessed part of the request path
     * @param method the method being invoked
     * @param targetUri the URI of the target resource
     * @param headers request headers
     * @param principal the {@link Principal} making this request, {@code null} if not known
     * @return A {@link PreparedStatement}
     */
    @NonNullByDefault
    private PreparedRequest prepare(final SegmentPeeler peeler, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal) {
        LOG.debug("Preparing {} {}", method, targetUri);

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

        final var segment = peeler.next();
        final var path = peeler.remaining();
        return switch (segment) {
            case "data" -> prepareData(method, targetUri, headers, principal, path);
            case "operations" -> prepareOperations(method, targetUri, headers, principal, path);
            case "yang-library-version" -> prepareYangLibraryVersion(method, targetUri, headers, principal, path);
            case "modules" -> prepareModules(method, targetUri, headers, principal, path);
            default -> NOT_FOUND;
        };
    }

    /**
     * Process a request to <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.1">RFC 8040 {+restconf}/data</a>
     * resource.
     */
    @NonNullByDefault
    private PreparedRequest prepareData(final ImplementedMethod method, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return switch (method) {
            case DELETE -> prepareDataDelete(targetUri, headers, principal, path);
            case GET -> prepareDataGet(targetUri, headers, principal, path, true);
            case HEAD -> prepareDataGet(targetUri, headers, principal, path, false);
            case OPTIONS -> prepareDataOptions(targetUri, principal, path);
            case PATCH -> prepareDataPatch(targetUri, headers, principal, path);
            case POST -> prepareDataPost(targetUri, headers, principal, path);
            case PUT -> prepareDataPut(targetUri, headers, principal, path);
        };
    }

    // delete target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.7
    @NonNullByDefault
    private PreparedRequest prepareDataDelete(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return path.isEmpty() ? METHOD_NOT_ALLOWED_DATASTORE
            : requiredApiPath(path, apiPath -> new PendingDataDelete(invariants, targetUri, principal, apiPath));
    }

    // retrieve data and metadata for a resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.3
    // HEAD is same as GET but without content -> https://www.rfc-editor.org/rfc/rfc8040#section-4.2
    @NonNullByDefault
    private PreparedRequest prepareDataGet(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean withContent) {
        // Attempt to choose an encoding based on user's preference. If we cannot pick one, responding with a 406 status
        // and list the encodings we support
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? NOT_ACCEPTABLE_DATA : optionalApiPath(path,
            apiPath -> new PendingDataGet(invariants, targetUri, principal, encoding, apiPath, withContent));
    }

    // resource options -> https://www.rfc-editor.org/rfc/rfc8040#section-4.1
    @NonNullByDefault
    private PreparedRequest prepareDataOptions(final URI targetUri, final @Nullable Principal principal,
            final String path) {
        return optionalApiPath(path, apiPath -> new PendingDataOptions(invariants, targetUri, principal, apiPath));
    }

    // PATCH -> https://www.rfc-editor.org/rfc/rfc8040#section-4.6
    @NonNullByDefault
    private PreparedRequest prepareDataPatch(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        final var contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null) {
            return UNSUPPORTED_MEDIA_TYPE_PATCH;
        }
        final var mimeType = HttpUtil.getMimeType(contentType);
        if (mimeType == null) {
            return UNSUPPORTED_MEDIA_TYPE_PATCH;
        }
        final var mediaType = AsciiString.of(mimeType);

        for (var encoding : MessageEncoding.values()) {
            // FIXME: tighten this check to just dataMediaType
            if (encoding.producesDataCompatibleWith(mediaType)) {
                // Plain RESTCONF patch = merge target resource content ->
                // https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1
                return optionalApiPath(path,
                    apiPath -> new PendingDataPatchPlain(invariants, targetUri, principal, encoding, apiPath));
            }
            if (encoding.patchMediaType().equals(mediaType)) {
                // YANG Patch = ordered list of edits that are applied to the target datastore ->
                // https://www.rfc-editor.org/rfc/rfc8072#section-2
                final var accept = chooseOutputEncoding(headers);
                return accept == null ? NOT_ACCEPTABLE_DATA : optionalApiPath(path,
                    apiPath -> new PendingDataPatchYang(invariants, targetUri, principal, encoding, accept, apiPath));
            }
        }

        return UNSUPPORTED_MEDIA_TYPE_PATCH;
    }

    // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
    // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
    @NonNullByDefault
    private PreparedRequest prepareDataPost(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return switch (chooseInputEncoding(headers)) {
            case UNRECOGNIZED, UNSPECIFIED -> UNSUPPORTED_MEDIA_TYPE_DATA;
            case NOT_PRESENT -> prepareDataPost(targetUri, headers, principal, path, invariants.defaultEncoding());
            case JSON -> prepareDataPost(targetUri, headers, principal, path, MessageEncoding.JSON);
            case XML -> prepareDataPost(targetUri, headers, principal, path, MessageEncoding.XML);
        };
    }

    @NonNullByDefault
    private PreparedRequest prepareDataPost(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final MessageEncoding content) {
        if (path.isEmpty()) {
            return new PendingDataCreate(invariants, targetUri, principal, content);
        }

        final var accept = chooseOutputEncoding(headers);
        return accept == null ? NOT_ACCEPTABLE_DATA
            : requiredApiPath(path,
                apiPath -> new PendingDataPost(invariants, targetUri, principal, content, accept, apiPath));
    }

    // create or replace target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.5
    @NonNullByDefault
    private PreparedRequest prepareDataPut(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return switch (chooseInputEncoding(headers)) {
            case UNRECOGNIZED, UNSPECIFIED -> UNSUPPORTED_MEDIA_TYPE_DATA;
            case NOT_PRESENT -> prepareDataPut(targetUri, principal, path, invariants.defaultEncoding());
            case JSON -> prepareDataPut(targetUri, principal, path, MessageEncoding.JSON);
            case XML -> prepareDataPut(targetUri, principal, path, MessageEncoding.XML);
        };
    }

    @NonNullByDefault
    private PreparedRequest prepareDataPut(final URI targetUri, final @Nullable Principal principal, final String path,
            final MessageEncoding encoding) {
        return optionalApiPath(path,
            apiPath -> new PendingDataPut(invariants, targetUri, principal, encoding, apiPath));
    }

    /**
     * Prepare a request to
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC 8040 {+restconf}/operations</a> resource.
     */
    @NonNullByDefault
    private PreparedRequest prepareOperations(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        return switch (method) {
            case GET -> prepareOperationsGet(targetUri, headers, principal, path, true);
            case HEAD -> prepareOperationsGet(targetUri, headers, principal, path, false);
            case OPTIONS -> prepareOperationsOptions(targetUri, principal, path);
            case POST -> prepareOperationsPost(targetUri, headers, principal, path);
            default -> prepareOperationsDefault(targetUri, path);
        };
    }

    @NonNullByDefault
    private PreparedRequest prepareOperationsGet(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean withContent) {
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? NOT_ACCEPTABLE_DATA : optionalApiPath(path,
            apiPath -> new PendingOperationsGet(invariants, targetUri, principal, encoding, apiPath, withContent));
    }

    @NonNullByDefault
    private PreparedRequest prepareOperationsOptions(final URI targetUri, final @Nullable Principal principal,
            final String path) {
        return path.isEmpty() ? AbstractPendingOptions.READ_ONLY
            : requiredApiPath(path, apiPath -> new PendingOperationsOptions(invariants, targetUri, principal, apiPath));
    }

    // invoke rpc -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2
    @NonNullByDefault
    private PreparedRequest prepareOperationsPost(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        final var accept = chooseOutputEncoding(headers);
        return accept == null ? NOT_ACCEPTABLE_DATA : switch (chooseInputEncoding(headers)) {
            case NOT_PRESENT ->
                prepareOperationsPost(targetUri, principal, path, invariants.defaultEncoding(), accept);
            case JSON -> prepareOperationsPost(targetUri, principal, path, MessageEncoding.JSON, accept);
            case XML -> prepareOperationsPost(targetUri, principal, path, MessageEncoding.XML, accept);
            case UNRECOGNIZED, UNSPECIFIED -> UNSUPPORTED_MEDIA_TYPE_DATA;
        };
    }

    @NonNullByDefault
    private PreparedRequest prepareOperationsPost(final URI targetUri, final @Nullable Principal principal,
            final String path, final MessageEncoding content, final MessageEncoding accept) {
        return optionalApiPath(path,
            apiPath -> new PendingOperationsPost(invariants, targetUri, principal, content, accept, apiPath));
    }

    @NonNullByDefault
    private static PreparedRequest prepareOperationsDefault(final URI targetUri, final String path) {
        return path.isEmpty() ? METHOD_NOT_ALLOWED_READ_ONLY
            // TODO: This is incomplete. We are always reporting 405 Method Not Allowed, but we can do better.
            //       We should fire off an OPTIONS request for the apiPath and see if it exists: if it does not,
            //       we should report a 404 Not Found instead.
            : requiredApiPath(path, apiPath -> METHOD_NOT_ALLOWED_RPC);
    }

    /**
     * Prepare a request to
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">{+restconf}/yang-library-version</a> resource.
     */
    @NonNullByDefault
    private PreparedRequest prepareYangLibraryVersion(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        return !path.isEmpty() ? NOT_FOUND : switch (method) {
            case GET -> prepareYangLibraryVersionGet(targetUri, headers, principal, true);
            case HEAD -> prepareYangLibraryVersionGet(targetUri, headers, principal, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    @NonNullByDefault
    private PreparedRequest prepareYangLibraryVersionGet(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final boolean withContent) {
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? UNSUPPORTED_MEDIA_TYPE_DATA
            : new PendingYangLibraryVersionGet(invariants, targetUri, principal, encoding, withContent);
    }

    /**
     * Access to YANG modules.
     */
    @NonNullByDefault
    private PreparedRequest prepareModules(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        return switch (method) {
            case GET -> prepareModulesGet(targetUri, headers, principal, path, true);
            case HEAD -> prepareModulesGet(targetUri, headers, principal, path, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    @NonNullByDefault
    private PreparedRequest prepareModulesGet(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean withContent) {
        if (path.isEmpty()) {
            return NOT_FOUND;
        }

        // optional mountPath followed by file name separated by slash
        final var str = path.substring(1);
        final var lastSlash = str.lastIndexOf('/');
        final ApiPath mountPath;
        final String fileName;
        if (lastSlash != -1) {
            final var mountString = str.substring(0, lastSlash);
            try {
                mountPath = ApiPath.parse(mountString);
            } catch (ParseException e) {
                return badApiPath(mountString, e);
            }
            fileName = str.substring(lastSlash + 1);
        } else {
            mountPath = ApiPath.empty();
            fileName = str;
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

        return doYin ? new PendingModulesGetYin(invariants, targetUri, principal, mountPath, decoded)
            : new PendingModulesGetYang(invariants, targetUri, principal, mountPath, decoded);
    }

    @NonNullByDefault
    private static PreparedRequest optionalApiPath(final String path, final Function<ApiPath, PreparedRequest> func) {
        return path.isEmpty() ? func.apply(ApiPath.empty()) : requiredApiPath(path, func);
    }

    @NonNullByDefault
    private static PreparedRequest requiredApiPath(final String path, final Function<ApiPath, PreparedRequest> func) {
        final ApiPath apiPath;
        final var str = path.substring(1);
        try {
            apiPath = ApiPath.parse(str);
        } catch (ParseException e) {
            return badApiPath(str, e);
        }
        return func.apply(apiPath);
    }

    private static @NonNull CompletedRequest badApiPath(final String path, final ParseException cause) {
        LOG.debug("Failed to parse API path", cause);
        return new DefaultCompletedRequest(HttpResponseStatus.BAD_REQUEST, null,
            ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT,
                "Bad request path '%s': '%s'".formatted(path, cause.getMessage())));
    }

    @NonNullByDefault
    private static RequestBodyHandling chooseInputEncoding(final HttpHeaders headers) {
        if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
            return RequestBodyHandling.NOT_PRESENT;
        }
        final var contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null) {
            // No Content-Type
            return RequestBodyHandling.UNSPECIFIED;
        }
        final var mimeType = HttpUtil.getMimeType(contentType);
        if (mimeType == null) {
            // Content-Type without a proper media type
            return RequestBodyHandling.UNSPECIFIED;
        }
        final var mediaType = AsciiString.of(mimeType);
        if (MessageEncoding.JSON.producesDataCompatibleWith(mediaType)) {
            return RequestBodyHandling.JSON;
        }
        if (MessageEncoding.XML.producesDataCompatibleWith(mediaType)) {
            return RequestBodyHandling.XML;
        }
        return RequestBodyHandling.UNRECOGNIZED;
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

    // FIXME: this algorithm is quite naive and ignores https://www.rfc-editor.org/rfc/rfc9110#name-accept, i.e.
    //        it does not handle wildcards at all.
    //        furthermore it completely ignores https://www.rfc-editor.org/rfc/rfc9110#name-quality-values, i.e.
    //        it does not consider client-supplied weights during media type selection AND it treats q=0 as an
    //        inclusion of a media type rather than its exclusion
    private static @Nullable MessageEncoding matchEncoding(final String acceptValue) {
        final var mimeType = HttpUtil.getMimeType(acceptValue);
        if (mimeType != null) {
            final var mediaType = AsciiString.of(mimeType);
            for (var encoding : MessageEncoding.values()) {
                if (encoding.producesDataCompatibleWith(mediaType)) {
                    return encoding;
                }
            }
        }
        return null;
    }
}
