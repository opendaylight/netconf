/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.HTTPServerSession;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.restconf.server.api.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
// FIXME: HTTP/1.1 and HTTP/2 behave differently w.r.t. incoming requests and their servicing:
//        1. HTTP/1.1 uses pipelining, therefore:
//           - we cannot halt incoming pipeline
//           - we may run request prepare() while a request is executing in the background, but
//           - we MUST send responses out in the same order we have received them
//           - SSE GET turns the session into a sender, i.e. no new requests can be processed
//        2. HTTP/2 uses concurrent execution, therefore
//           - we need to track which streams are alive and support terminating pruning requests when client resets
//             a stream
//           - SSE is nothing special
//           - we have Http2Settings, which has framesize -- which we should use when streaming responses
//        We support HTTP/1.1 -> HTTP/2 upgrade for the first request only -- hence we know before processing the first
//        result which mode of operation is effective. We probably need to have two subclasses of this thing, with
//        HTTP/1.1 and HTTP/2 specializations.
final class RestconfSession extends HTTPServerSession implements TransportSession, PendingRequestListener {
    @NonNullByDefault
    private record RequestContext(ChannelHandlerContext ctx, HttpVersion version, @Nullable Integer streamId) {
        RequestContext {
            requireNonNull(ctx);
            requireNonNull(version);
        }

        void respond(final FullHttpResponse response) {
            HTTPServerSession.respond(ctx, streamId, response);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(RestconfSession.class);

    private final ConcurrentMap<PendingRequest<?>, RequestContext> executingRequests = new ConcurrentHashMap<>();
    private final EndpointRoot root;

    RestconfSession(final HttpScheme scheme, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
    }

    @Override
    protected void processRequest(final ChannelHandlerContext ctx, final Integer streamId,
            final ImplementedMethod method, final URI targetUri, final HttpVersion version, final HttpHeaders headers,
            final ByteBuf body) {
        switch (root.prepareRequest(this, method, targetUri, headers)) {
            case CompletedRequest completed -> {
                body.release();
                respond(ctx, streamId, completed.toHttpResponse(version));
            }
            case PendingRequest<?> pending -> {
                LOG.debug("Dispatching {} {}", method, targetUri);
                executeRequest(new RequestContext(ctx, version, streamId), pending, body);
            }
        }
    }

    private void executeRequest(final RequestContext context, final PendingRequest<?> pending, final ByteBuf content) {
        // We are invoked with content's reference and need to make sure it gets released.
        final ByteBufInputStream body;
        if (content.isReadable()) {
            body = new ByteBufInputStream(content, true);
        } else {
            content.release();
            body = null;
        }

        // Remember metadata about the request and then execute it
        executingRequests.put(pending, context);
        pending.execute(this, body);
    }

    @Override
    public void requestComplete(final PendingRequest<?> request, final Response response) {
        final var context = executingRequests.remove(request);
        if (context != null) {
            context.respond(switch (response) {
                case CompletedRequest completed -> completed.toHttpResponse(context.version);
                case CharSourceResponse charSource -> formatResponse(charSource, context.version);
                case FormattableDataResponse formattable -> formatResponse(formattable, context.version);
            });
        } else {
            LOG.warn("Cannot pair request {}, not sending response {}", request, response, new Throwable());
        }
    }

    @Override
    public void requestFailed(final PendingRequest<?> request, final Exception cause) {
        LOG.warn("Internal error while processing {}", request, cause);
        final var context = executingRequests.remove(request);
        if (context != null) {
            context.respond(formatException(cause, context.version));
        } else {
            LOG.warn("Cannot pair request, not sending response", new Throwable());
        }
    }

    @NonNullByDefault
    private static FullHttpResponse formatException(final Exception cause, final HttpVersion version) {
        final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        final var content = response.content();
        // Note: we are tempted to do a cause.toString() here, but we are dealing with unhandled badness here,
        //       so we do not want to be too revealing -- hence a message is all the user gets.
        ByteBufUtil.writeUtf8(content, cause.getMessage());
        HttpUtil.setContentLength(response, content.readableBytes());
        return response;
    }

    // FIXME: below payloads use a synchronous dump of data into the socket. We cannot safely do that on the event loop,
    //        because a slow client would end up throttling our IO threads simply because of TCP window and similar
    //        queuing/backpressure things.
    //
    //        we really want to kick off a virtual thread to take care of that, i.e. doing its own synchronous write
    //        thing, talking to a short queue (SPSC?) of HttpObjects.
    //
    //        the event loop of each channel would be the consumer of that queue, picking them off as quickly as
    //        possible, but execting backpressure if the amount of pending stuff goes up.
    //
    //        as for the HttpObjects: this effectively means that the OutputStreams used in the below code should be
    //        replaced with entities which perform chunking:
    //        - buffer initial stuff, so that we produce a FullHttpResponse if the payload is below
    //          256KiB (or so), i.e. producing Content-Length header and dumping the thing in one go
    //        - otherwise emit just HttpResponse with Transfer-Enconding: chunked and continue sending
    //          out chunks (of reasonable size).
    //        - finish up with a LastHttpContent

    @NonNullByDefault
    private static FullHttpResponse formatResponse(final CharSourceResponse response, final HttpVersion version) {
        final var httpResponse = new DefaultFullHttpResponse(version, HttpResponseStatus.OK);
        try (var os = new ByteBufOutputStream(httpResponse.content())) {
            response.source().asByteSource(StandardCharsets.UTF_8).copyTo(os);
        } catch (IOException e) {
            LOG.warn("IO error while converting stream body", e);
            return formatException(e, version);
        }

        return setContentHeaders(httpResponse, response.mediaType());
    }

    @NonNullByDefault
    private static FullHttpResponse formatResponse(final FormattableDataResponse response, final HttpVersion version) {
        final var httpResponse = new DefaultFullHttpResponse(version, response.status());
        try (var os = new ByteBufOutputStream(httpResponse.content())) {
            response.writeTo(os);
        } catch (IOException e) {
            LOG.warn("IO error while converting formattable body", e);
            return formatException(e, version);
        }

        final var extra = response.headers();
        if (extra != null) {
            httpResponse.headers().set(extra);
        }

        return setContentHeaders(httpResponse, response.encoding().dataMediaType());
    }

    @NonNullByDefault
    private static FullHttpResponse setContentHeaders(final FullHttpResponse response, final AsciiString contentType) {
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, contentType)
            .set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }
}
