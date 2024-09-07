/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.AsciiString;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
final class RestconfSession extends SimpleChannelInboundHandler<FullHttpRequest> implements TransportSession {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfSession.class);
    private static final AsciiString STREAM_ID = ExtensionHeaderNames.STREAM_ID.text();

    private final Map<Integer, Registration> senders = new HashMap<>();
    private final RestconfStream.Registry streamRegistry;
    private final RestconfRequestDispatcher dispatcher;
    private final WellKnownResources wellKnown;

    RestconfSession(final WellKnownResources wellKnown, final RestconfStream.Registry streamRegistry,
            final RestconfRequestDispatcher dispatcher) {
        super(FullHttpRequest.class, false);
        this.wellKnown = requireNonNull(wellKnown);
        this.streamRegistry = requireNonNull(streamRegistry);
        this.dispatcher = requireNonNull(dispatcher);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (cause instanceof StreamException se) {
            final var sender = senders.remove(se.streamId());
            if (sender != null) {
                sender.close();
                return;
            }
        }
        super.exceptionCaught(ctx, cause);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        // non-null indicates HTTP/2 request, which we need to propagate to any response
        final var streamId = msg.headers().getInt(STREAM_ID);
        final var version = msg.protocolVersion();
        final var decoder = new QueryStringDecoder(msg.uri());
        final var path = decoder.path();

        if (path.startsWith("/.well-known/")) {
            // Well-known resources are immediately available and are trivial to service
            respond(ctx, streamId, wellKnown.request(version, msg.method(), path.substring(13)));
            msg.release();
        } else if (path.startsWith("/streams/")) {
            // Event streams are immediately available, but involve tricky state management
            streamsRequest(ctx, version, streamId, decoder, msg, path.substring(9));
            msg.release();
        } else {
            // Defer to dispatcher
            dispatchRequest(ctx, version, streamId, decoder, msg);
        }
    }

    private void streamsRequest(final ChannelHandlerContext ctx, final HttpVersion version, final Integer streamId,
            final QueryStringDecoder decoder, final FullHttpRequest request, final String suffix) {
        // uri is expected to be in the format <encodingName>/<streamName>
        final int slash = suffix.indexOf('/');
        if (slash != -1) {
            LOG.debug("Malformed stream URI '{}'", suffix);
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.NOT_FOUND));
            return;
        }

        final String encodingName = suffix.substring(0, slash);
        final EncodingName encoding;
        try {
            encoding = new EncodingName(encodingName);
        } catch (IllegalArgumentException e) {
            LOG.debug("Stream encoding name '{}' is invalid", encodingName, e);
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.NOT_FOUND));
            return;
        }

        final var streamName = suffix.substring(slash + 1);
        final var stream = streamRegistry.lookupStream(streamName);
        if (stream == null) {
            LOG.debug("Stream '{}' not found", streamName);
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.NOT_FOUND));
            return;
        }

        // We only support GET method
        if (!HttpMethod.GET.equals(request.method())) {
            final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.NOT_IMPLEMENTED);
            response.headers().set(HttpHeaderNames.ALLOW, HttpMethod.GET);
            respond(ctx, streamId, response);
            return;
        }

        // We only support text/event-stream
        if (!request.headers().contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, false)) {
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.NOT_ACCEPTABLE));
            return;
        }

        final EventStreamGetParams streamParams;
        try {
            streamParams = EventStreamGetParams.of(QueryParameters.ofMultiValue(decoder.parameters()));
        } catch (IllegalArgumentException e) {
            // FIXME: report an error
            return;
        }

        // We have everything we need from the request and are ready to establish the subscription. We do different
        // for HTTP/1 and HTTP/2.
        if (streamId != null) {
            addEventStream(ctx, version, streamId, stream, encoding, streamParams);
        } else {
            switchToEventStream(ctx, version, stream, encoding, streamParams);
        }
    }

    // HTTP/1 event stream start. This amounts to a 'long GET', i.e. if our subscription attempt is successful, we will
    // not be servicing any other requests.
    private void switchToEventStream(final ChannelHandlerContext ctx, final HttpVersion version,
            final RestconfStream<?> stream, final EncodingName encoding, final EventStreamGetParams params) {
        final var sender = new ChannelSender();
        final var registration = registerSender(ctx, version, null, stream, encoding, params, sender);
        if (registration == null) {
            return;
        }

        // Replace ourselves with the sender and enable it wil the registration
        ctx.channel().pipeline().replace(this, null, sender);
        sender.enable(registration);
    }

    // HTTP/2 event stream start.
    private void addEventStream(final ChannelHandlerContext ctx, final HttpVersion version, final Integer streamId,
            final RestconfStream<?> stream, final EncodingName encoding, final EventStreamGetParams params) {
        final var sender = new StreamSender(streamId);
        final var registration = registerSender(ctx, version, streamId, stream, encoding, params, sender);
        if (registration == null) {
            return;
        }

        // Attach the
        senders.put(streamId, registration);



        // FIXME: add the sender to our a hashmap so we can respond to it being reset
    }

    private static @Nullable Registration registerSender(final ChannelHandlerContext ctx, final HttpVersion version,
            final Integer streamId, final RestconfStream<?> stream, final EncodingName encoding,
            final EventStreamGetParams params, final Sender sender) {
        final Registration reg;
        try {
            reg = stream.addSubscriber(sender, encoding, params);
        } catch (UnsupportedEncodingException | XPathExpressionException e) {
            // FIXME: report an error
            return null;
        }

        if (reg == null) {
            LOG.debug("Stream {} disappeared while subscribing", stream);
            respond(ctx, streamId, new DefaultFullHttpResponse(version, HttpResponseStatus.NOT_FOUND));
        }
        return reg;
    }


    private void dispatchRequest(final ChannelHandlerContext ctx, final HttpVersion version, final Integer streamId,
            final QueryStringDecoder decoder, final FullHttpRequest msg) {
        dispatcher.dispatch(decoder, msg, new FutureCallback<>() {
            @Override
            public void onSuccess(final FullHttpResponse response) {
                msg.release();
                respond(ctx, streamId, response);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                msg.release();

                final var message = throwable.getMessage();
                final var content = message == null ? Unpooled.EMPTY_BUFFER
                    : ByteBufUtil.writeUtf8(ctx.alloc(), message);
                final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    content);
                response.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
                respond(ctx, streamId, response);
            }
        });
    }

    private static void respond(final ChannelHandlerContext ctx, final Integer streamId,
            final FullHttpResponse response) {
        if (streamId != null) {
            response.headers().setInt(STREAM_ID, streamId);
        }
        ctx.writeAndFlush(response);
    }
}
