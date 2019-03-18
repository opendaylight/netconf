/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import java.util.Optional;
import org.opendaylight.restconf.common.configuration.RestconfConfigurationHolder;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link WebSocketServerHandler} is implementation of {@link SimpleChannelInboundHandler} which allow handle
 * {@link FullHttpRequest} and {@link WebSocketFrame} messages.
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketServerHandler.class);

    private final RestconfConfigurationHolder.SecurityType securityType;
    private WebSocketServerHandshaker handshaker;

    WebSocketServerHandler(final RestconfConfigurationHolder.SecurityType securityType) {
        this.securityType = securityType;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    /**
     * Checks if HTTP request method is GET and if is possible to decode HTTP result of request.
     *
     * @param ctx ChannelHandlerContext
     * @param req FullHttpRequest
     */
    private void handleHttpRequest(final ChannelHandlerContext ctx, final FullHttpRequest req) {
        // Handle a bad request.
        if (!req.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        // Allow only GET methods.
        if (req.method() != HttpMethod.GET) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN));
            return;
        }

        final String streamName = ListenersBroker.createStreamNameFromUri(req.uri());
        if (streamName.contains(RestconfConstants.DATA_SUBSCR)) {
            final Optional<ListenerAdapter> listener =
                    ListenersBroker.getInstance().getDataChangeListenerFor(streamName);
            if (listener.isPresent()) {
                listener.get().addSubscriber(ctx.channel());
                LOG.debug("Subscriber successfully registered.");
            } else {
                LOG.error("Listener for stream with name '{}' was not found.", streamName);
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            }
        } else if (streamName.contains(RestconfConstants.NOTIFICATION_STREAM)) {
            final Optional<NotificationListenerAdapter> listener =
                    ListenersBroker.getInstance().getNotificationListenerFor(streamName);
            if (listener.isPresent()) {
                listener.get().addSubscriber(ctx.channel());
                LOG.debug("Subscriber successfully registered.");
            } else {
                LOG.error("Listener for stream with name '{}' was not found.", streamName);
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            }
        }

        // Handshake
        final WebSocketServerHandshakerFactory wsFactory =
                new WebSocketServerHandshakerFactory(getWebSocketLocation(req),
                        null, false);
        this.handshaker = wsFactory.newHandshaker(req);
        if (this.handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            this.handshaker.handshake(ctx.channel(), req);
        }

    }

    /**
     * Checks response status, send response and close connection if necessary.
     *
     * @param ctx ChannelHandlerContext
     * @param req HttpRequest
     * @param res FullHttpResponse
     */
    private static void sendHttpResponse(final ChannelHandlerContext ctx, final HttpRequest req,
                                         final FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        final boolean notOkay = !HttpResponseStatus.OK.equals(res.status());
        if (notOkay) {
            res.content().writeCharSequence(res.status().toString(), CharsetUtil.UTF_8);
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }

        // Send the response and close the connection if necessary.
        final ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (notOkay || !HttpUtil.isKeepAlive(req)) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Handles web socket frame.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param frame {@link WebSocketFrame}
     */
    private void handleWebSocketFrame(final ChannelHandlerContext ctx, final WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            this.handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            final String streamName = ListenersBroker.createStreamNameFromUri(
                    ((CloseWebSocketFrame) frame).reasonText());
            if (streamName.contains(RestconfConstants.DATA_SUBSCR)) {
                final Optional<ListenerAdapter> listener = ListenersBroker.getInstance()
                        .getDataChangeListenerFor(streamName);
                if (listener.isPresent()) {
                    listener.get().removeSubscriber(ctx.channel());
                    LOG.debug("Subscriber successfully removed.");
                    if (!listener.get().hasSubscribers()) {
                        ListenersBroker.getInstance().removeAndCloseDataChangeListener(listener.get());
                    }
                }
            } else if (streamName.contains(RestconfConstants.NOTIFICATION_STREAM)) {
                final Optional<NotificationListenerAdapter> listener
                        = ListenersBroker.getInstance().getNotificationListenerFor(streamName);
                if (listener.isPresent()) {
                    listener.get().removeSubscriber(ctx.channel());
                    LOG.debug("Subscriber successfully removed.");
                    if (!listener.get().hasSubscribers()) {
                        ListenersBroker.getInstance().removeAndCloseNotificationListener(listener.get());
                    }
                }
            }
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        ctx.close();
    }

    /**
     * Get web socket location from HTTP request.
     *
     * @param httpRequest HTTP request from which the location will be returned.
     * @return String representation of web socket location.
     */
    private String getWebSocketLocation(final HttpRequest httpRequest) {
        String protocolName;
        switch (securityType) {
            case DISABLED:
                protocolName = RestconfStreamsConstants.SCHEMA_SUBSCRIBE_URI;
                break;
            case TLS_AUTH_PRIV:
                protocolName = RestconfStreamsConstants.SCHEMA_SUBSCRIBE_SECURED_URI;
                break;
            default:
                protocolName = RestconfStreamsConstants.SCHEMA_SUBSCRIBE_URI;
        }
        return protocolName + "://" + httpRequest.headers().get(HttpHeaderNames.HOST) + httpRequest.uri();
    }
}