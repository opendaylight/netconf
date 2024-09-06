/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

/**
 * An {@link HTTPServer} operating over plain TCP.
 */
final class PlainHTTPServer extends HTTPServer {
    PlainHTTPServer(final TransportChannelListener listener, final RequestDispatcher dispatcher,
            final AuthHandlerFactory authHandlerFactory) {
        super(listener, dispatcher, authHandlerFactory);
    }

    @Override
    void initializePipeline(final ChannelPipeline pipeline, final Http2ConnectionHandler connectionHandler) {
        // Cleartext upgrade flow
        final var sourceCodec = new HttpServerCodec();
        pipeline
            .addLast(new CleartextHttp2ServerUpgradeHandler(
                sourceCodec,
                new HttpServerUpgradeHandler(sourceCodec,
                    protocol -> AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)
                        ? new Http2ServerUpgradeCodec(connectionHandler) : null, MAX_HTTP_CONTENT_LENGTH),
                connectionHandler))
            .addLast(new SimpleChannelInboundHandler<HttpMessage>() {
                @Override
                protected void channelRead0(final ChannelHandlerContext ctx, final HttpMessage request) {
                    // if there was no upgrade to HTTP/2 the incoming message is accepted via channel read;
                    // configure HTTP 1.1 flow, pass the message further the pipeline, remove self as no longer required
                    ctx.pipeline()
                        .addAfter(ctx.name(), null, new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH))
                        .replace(this, null, new HttpServerKeepAliveHandler());
                    ctx.fireChannelRead(ReferenceCountUtil.retain(request));
                }

                @Override
                public void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
                    // if there was upgrade to HTTP/2 the upgrade event is fired further the pipeline;
                    // on event occurrence it's only required to complete the configuration for future requests,
                    // then remove self as no longer required
                    if (event instanceof HttpServerUpgradeHandler.UpgradeEvent) {
                        ctx.pipeline().remove(this);
                    }
                    super.userEventTriggered(ctx, event);
                }
            });
    }
}
