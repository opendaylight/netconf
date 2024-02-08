/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import java.nio.charset.StandardCharsets;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.client.authentication.users.user.auth.type.Basic;

class ServerChannelInitializer extends ChannelInitializer<Channel> {

    private static final int MAX_HTTP_CONTENT_LENGTH = 16 * 1024;

    private final ChannelHandler authHandler;
    private final RequestDispatcher dispatcher;

    ServerChannelInitializer(final HttpServerGrouping httpParams, final RequestDispatcher dispatcher) {
        super();
        this.authHandler = authHandler(httpParams);
        this.dispatcher = dispatcher;
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        channel.pipeline().addLast(new HttpServerCodec());
        channel.pipeline().addLast(new HttpServerKeepAliveHandler());
        channel.pipeline().addLast(new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
        if (authHandler != null) {
            channel.pipeline().addLast(authHandler);
        }
        channel.pipeline().addLast(serverHandler(dispatcher));
    }

    private static ChannelHandler authHandler(final HttpServerGrouping httpParams) {
        if (httpParams == null || httpParams.getClientAuthentication() == null) {
            return null;
        }
        final var userMap = ImmutableMap.<String, String>builder();
        httpParams.getClientAuthentication().nonnullUsers().nonnullUser().forEach(
            (ignored, user) -> {
                if (user.getAuthType() instanceof Basic basicAuth) {
                    userMap.put(basicAuth.getBasic().requireUsername(),
                        basicAuth.getBasic().nonnullPassword().requireHashedPassword().getValue());
                }
            }
        );
        return new BasicAuthHandler(userMap.build());
    }

    private static ChannelHandler serverHandler(final RequestDispatcher dispatcher) {
        return new SimpleChannelInboundHandler<FullHttpRequest>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request)
                    throws Exception {
                // NB using request' copy to disconnect the content data from channel's buffer allocated.
                // this prevents the content data became inaccessible once byte buffer of original message is released
                // on exit of current method
                Futures.addCallback(dispatcher.dispatch(request.copy()),
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(final FullHttpResponse result) {
                            ctx.writeAndFlush(result);
                        }

                        @Override
                        public void onFailure(final Throwable throwable) {
                            final var message = throwable.getMessage();
                            final var content = message == null ? Unpooled.EMPTY_BUFFER
                                : Unpooled.wrappedBuffer(message.getBytes(StandardCharsets.UTF_8));
                            final var response = new DefaultFullHttpResponse(request.protocolVersion(),
                                INTERNAL_SERVER_ERROR, content);
                            response.headers().set(CONTENT_TYPE, TEXT_PLAIN)
                                .setInt(CONTENT_LENGTH, response.content().readableBytes());
                            ctx.writeAndFlush(response);
                        }
                    }, MoreExecutors.directExecutor());
            }
        };
    }
}
