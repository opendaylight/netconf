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
import static org.opendaylight.netconf.transport.http.BasicAuthHandler.BASIC_AUTH_PREFIX;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208.password.grouping.password.type.CleartextPassword;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.client.identity.auth.type.Basic;

/**
 * Netty channel initializer for Http Client.
 */
class ClientChannelInitializer extends ChannelInitializer<Channel> implements HttpChannelInitializer {
    private static final int MAX_HTTP_CONTENT_LENGTH = 16 * 1024;

    private final SettableFuture<Void> completeFuture = SettableFuture.create();
    private final ChannelHandler authHandler;
    private final ChannelHandler dispatcherHandler;
    private final boolean http2;

    ClientChannelInitializer(final HttpClientGrouping httpParams, final ChannelHandler dispatcherHandler,
        final boolean http2) {
        authHandler = authHandler(httpParams);
        this.dispatcherHandler = requireNonNull(dispatcherHandler);
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
        if (authHandler != null) {
            pipeline.addLast(authHandler);
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
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
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
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                // trigger upgrade by simple GET request;
                // required headers and flow will be handled by HttpClientUpgradeHandler
                final var upgradeRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/", EMPTY_BUFFER);
                ctx.writeAndFlush(upgradeRequest);
                ctx.fireChannelActive();
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
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

    private static ChannelHandler authHandler(final HttpClientGrouping httpParams) {
        if (httpParams == null || httpParams.getClientIdentity() == null) {
            return null;
        }
        // Basic authorization handler, sets authorization header on outgoing requests
        final var authType = httpParams.getClientIdentity().getAuthType();
        if (authType instanceof Basic basicAuth) {
            final var username = basicAuth.getBasic().getUserId();
            final var password = basicAuth.getBasic().getPasswordType() instanceof CleartextPassword clearText
                ? clearText.requireCleartextPassword() : "";
            final var authHeader = BASIC_AUTH_PREFIX + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

            return new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                    throws Exception {
                    if (msg instanceof HttpRequest request) {
                        request.headers().set(HttpHeaderNames.AUTHORIZATION, authHeader);
                    }
                    super.write(ctx, msg, promise);
                }
            };
        }
        return null;
    }
}
