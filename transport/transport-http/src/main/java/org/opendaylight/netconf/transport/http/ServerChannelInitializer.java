/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.opendaylight.netconf.transport.http.Http2Utils.copyStreamId;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerGrouping;

/**
 * Netty channel initializer for Http Server.
 */
class ServerChannelInitializer extends ChannelInitializer<Channel> implements HttpChannelInitializer {
    private static final int MAX_HTTP_CONTENT_LENGTH = 16 * 1024;

    static final String REQUEST_DISPATCHER_HANDLER_NAME = "request-dispatcher";

    private final SettableFuture<Void> completeFuture = SettableFuture.create();
    private final AuthHandlerFactory authHandlerFactory;
    private final RequestDispatcher dispatcher;

    ServerChannelInitializer(final AuthHandlerFactory authHandlerFactory, final RequestDispatcher dispatcher) {
        this.authHandlerFactory = authHandlerFactory;
        this.dispatcher = dispatcher;
    }

    ServerChannelInitializer(final HttpServerGrouping httpParams, final RequestDispatcher dispatcher) {
        authHandlerFactory = BasicAuthHandlerFactory.ofNullable(httpParams);
        this.dispatcher = dispatcher;
    }

    @Override
    public ListenableFuture<Void> completeFuture() {
        return completeFuture;
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        final var pipeline = channel.pipeline();
        final var ssl = pipeline.get(SslHandler.class) != null;

        // External HTTP 2 to internal HTTP 1.1 adapter handler
        final var connectionHandler = Http2Utils.connectionHandler(true, MAX_HTTP_CONTENT_LENGTH);
        if (ssl) {
            // Application protocol negotiator over TLS
            pipeline.addLast(apnHandler(connectionHandler));
        } else {
            // Cleartext upgrade flow
            final var sourceCodec = new HttpServerCodec();
            final var upgradeHandler =
                new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory(connectionHandler));
            pipeline.addLast(new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, connectionHandler),
                upgradeResultHandler());
        }

        // signal server transport is ready to accept requests
        completeFuture.set(null);
    }

    private void configureEndOfPipeline(final ChannelPipeline pipeline) {
        if (authHandlerFactory != null) {
            pipeline.addLast(authHandlerFactory.create());
        }
        pipeline.addLast(REQUEST_DISPATCHER_HANDLER_NAME, serverHandler(dispatcher));
    }

    private ChannelHandler apnHandler(final ChannelHandler connectionHandler) {
        return new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) throws Exception {
                final var pipeline = ctx.pipeline();
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    pipeline.addLast(connectionHandler);
                    configureEndOfPipeline(pipeline);
                    return;
                }
                if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                    pipeline.addLast(new HttpServerCodec(),
                        new HttpServerKeepAliveHandler(),
                        new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
                    configureEndOfPipeline(pipeline);
                    return;
                }
                throw new IllegalStateException("unknown protocol: " + protocol);
            }
        };
    }

    private ChannelHandler upgradeResultHandler() {
        // the handler processes cleartext upgrade result

        return new SimpleChannelInboundHandler<HttpMessage>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final HttpMessage request) throws Exception {
                // if there was no upgrade to HTTP/2 the incoming message is accepted via channel read;
                // configure HTTP 1.1 flow, pass the message further the pipeline, remove self as no longer required
                final var pipeline = ctx.pipeline();
                pipeline.addLast(new HttpServerKeepAliveHandler(), new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
                configureEndOfPipeline(pipeline);
                ctx.fireChannelRead(ReferenceCountUtil.retain(request));
                pipeline.remove(this);
            }

            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
                // if there was upgrade to HTTP/2 the upgrade event is fired further the pipeline;
                // on event occurrence it's only required to complete the configuration for future requests,
                // then remove self as no longer required
                if (event instanceof HttpServerUpgradeHandler.UpgradeEvent) {
                    final var pipeline = ctx.pipeline();
                    configureEndOfPipeline(pipeline);
                    pipeline.remove(this);
                }
            }
        };
    }

    private static HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory(
            final Http2ConnectionHandler connectionHandler) {
        return protocol -> AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)
            ? new Http2ServerUpgradeCodec(connectionHandler) : null;
    }

    private static ChannelHandler serverHandler(final RequestDispatcher dispatcher) {
        return new SimpleChannelInboundHandler<FullHttpRequest>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) {
                dispatcher.dispatch(request.retain(), new FutureCallback<>() {
                    @Override
                    public void onSuccess(final FullHttpResponse response) {
                        copyStreamId(request, response);
                        request.release();
                        ctx.writeAndFlush(response);
                    }

                    @Override
                    public void onFailure(final Throwable throwable) {
                        final var message = throwable.getMessage();
                        final var content = message == null ? EMPTY_BUFFER
                            : Unpooled.wrappedBuffer(message.getBytes(StandardCharsets.UTF_8));
                        final var response = new DefaultFullHttpResponse(request.protocolVersion(),
                            INTERNAL_SERVER_ERROR, content);
                        response.headers()
                            .set(CONTENT_TYPE, TEXT_PLAIN)
                            .setInt(CONTENT_LENGTH, response.content().readableBytes());
                        onSuccess(response);
                    }
                });
            }
        };
    }
}
