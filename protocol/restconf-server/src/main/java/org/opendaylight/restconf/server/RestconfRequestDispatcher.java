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
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: rename to APIResource
final class RestconfRequestDispatcher extends AbstractResource {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequestDispatcher.class);

    private final Map<String, AbstractResource> resources;
    private final @NonNull PrincipalService principalService;
    private final @NonNull String firstSegment;
    private final @NonNull List<String> otherSegments;

    RestconfRequestDispatcher(final RestconfServer server, final PrincipalService principalService,
            final List<String> segments, final String restconfPath, final ErrorTagMapping errorTagMapping,
            final MessageEncoding defaultEncoding, final PrettyPrintParam defaultPrettyPrint) {
        super(new EndpointInvariants(server, defaultPrettyPrint, errorTagMapping, defaultEncoding,
            URI.create(requireNonNull(restconfPath))));
        this.principalService = requireNonNull(principalService);

        firstSegment = segments.getFirst();
        otherSegments = segments.stream().skip(1).collect(Collectors.toUnmodifiableList());

        resources = Map.of(
            "data", new DataResource(invariants),
            "operations", new OperationsResource(invariants),
            "yang-library-version", new YLVResource(invariants),
            "modules", new ModulesResource(invariants));

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

    @Override
    PreparedRequest prepare(final SegmentPeeler peeler, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final Principal principal) {
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
            LOG.debug("Not servicing root request");
            return NOT_FOUND;
        }

        final var segment = peeler.next();
        final var resource = resources.get(segment);
        if (resource != null) {
            return resource.prepare(peeler, method, targetUri, headers, principal);
        }

        LOG.debug("Resource for '{}' not found", segment);
        return NOT_FOUND;
    }
}
