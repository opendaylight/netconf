/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

/**
 * An {@link HTTPClient} operating over plain TCP.
 */
final class PlainHTTPClient extends HTTPClient {
    PlainHTTPClient(final TransportChannelListener<? super HTTPTransportChannel> listener,
            final ClientAuthProvider authProvider, final boolean http2) {
        super(listener, HTTPScheme.HTTP, authProvider, http2);
    }

    @Override
    void initializePipeline(final TransportChannel underlayChannel, final ChannelPipeline pipeline,
            final Http2ConnectionHandler connectionHandler) {
        // Cleartext upgrade flow
        final var sourceCodec = new HttpClientCodec();
        final var upgradeHandler = new HttpClientUpgradeHandler(sourceCodec,
            new Http2ClientUpgradeCodec(connectionHandler), MAX_HTTP_CONTENT_LENGTH);
        pipeline.addLast(sourceCodec, upgradeHandler, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                // Trigger upgrade with an OPTIONS request targetting the server itself, as per
                // https://www.rfc-editor.org/rfc/rfc7231#section-4.3.7
                // required headers and flow will be handled by HttpClientUpgradeHandler
                ctx.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "*"));
                ctx.fireChannelActive();
            }

            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                // process upgrade result
                if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
                    final var pipeline = ctx.pipeline();
                    configureEndOfPipeline(underlayChannel, pipeline);
                    pipeline.remove(this);
                } else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED) {
                    notifyTransportChannelFailed(new IllegalStateException("Server rejected HTTP/2 upgrade request"));
                }
            }
        });
    }
}
