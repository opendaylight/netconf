/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240316.HttpClientGrouping;

/**
 * Netty channel initializer for Http Client.
 */
final class ClientChannelInitializer extends ChannelInitializer<Channel> implements HttpChannelInitializer {
    private static final int MAX_HTTP_CONTENT_LENGTH = 16 * 1024;

    private final SettableFuture<Void> completeFuture = SettableFuture.create();
    private final ChannelHandler dispatcherHandler;
    private final ClientAuthProvider authProvider;
    private final boolean http2;

    ClientChannelInitializer(final HttpClientGrouping httpParams, final ChannelHandler dispatcherHandler,
            final boolean http2) {
        this.dispatcherHandler = requireNonNull(dispatcherHandler);
        authProvider = ClientAuthProvider.ofNullable(httpParams);
        this.http2 = http2;
    }

    @Override
    public ListenableFuture<Void> completeFuture() {
        return completeFuture;
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        final var pipeline = channel.pipeline();
        final boolean ssl = pipeline.get(SslHandler.class) != null;

        if (http2) {
            // External HTTP 2 to internal HTTP 1.1 adapter handler
            final var connectionHandler = Http2Utils.connectionHandler(false, MAX_HTTP_CONTENT_LENGTH);
            if (ssl) {
                // Application protocol negotiator over TLS
                pipeline.addLast(apnHandler(connectionHandler));
            } else {
                // Cleartext upgrade flow
                final var sourceCodec = new HttpClientCodec();
                final var upgradeHandler = new HttpClientUpgradeHandler(sourceCodec,
                    new Http2ClientUpgradeCodec(connectionHandler), MAX_HTTP_CONTENT_LENGTH);
                pipeline.addLast(sourceCodec, upgradeHandler, upgradeRequestHandler());
            }

        } else {
            // HTTP 1.1
            pipeline.addLast(new HttpClientCodec(), new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
            configureEndOfPipeline(pipeline);
        }
    }

    private void configureEndOfPipeline(final ChannelPipeline pipeline) {
        if (http2) {
            pipeline.addLast(Http2Utils.clientSettingsHandler());
        }
        if (authProvider != null) {
            pipeline.addLast(authProvider);
        }
        pipeline.addLast(dispatcherHandler);

        // signal client transport is ready to send requests
        // NB. while server signals readiness on exit from initChannel(),
        // client needs additional confirmation for upgrade completion in case of HTTP/2 cleartext flow
        completeFuture.set(null);
    }

    private ChannelHandler apnHandler(final ChannelHandler connectionHandler) {
        return new ApplicationProtocolNegotiationHandler("") {
            @Override
            protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    final var pipeline = ctx.pipeline();
                    pipeline.addLast(connectionHandler);
                    configureEndOfPipeline(pipeline);
                    return;
                }
                ctx.close();
                throw new IllegalStateException("unknown protocol: " + protocol);
            }
        };
    }

    protected ChannelHandler upgradeRequestHandler() {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                // trigger upgrade by simple GET request;
                // required headers and flow will be handled by HttpClientUpgradeHandler
                ctx.writeAndFlush(new DefaultFullHttpRequest(HTTP_1_1, GET, "/", EMPTY_BUFFER));
                ctx.fireChannelActive();
            }

            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
                // process upgrade result
                if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
                    configureEndOfPipeline(ctx.pipeline());
                    ctx.pipeline().remove(this);
                } else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED) {
                    completeFuture.setException(new IllegalStateException("Server rejected HTTP/2 upgrade request"));
                }
            }
        };
    }
}
