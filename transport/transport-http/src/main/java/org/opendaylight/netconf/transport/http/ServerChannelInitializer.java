/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.HttpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev230417.http.server.grouping.client.authentication.users.user.auth.type.basic.Basic;

class ServerChannelInitializer extends ChannelInitializer<Channel> {

    private static final int MAX_HTTP_CONTENT_LENGTH = 16 * 1024;

    private final ChannelHandler authHandler;
    private final RequestDispatcher dispatcher;

    ServerChannelInitializer(final HttpServerGrouping httpParams, final RequestDispatcher dispatcher) {
        super();
        this.authHandler = authHandler(requireNonNull(httpParams));
        this.dispatcher = dispatcher;
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        channel.pipeline().addLast(new HttpServerCodec());
        channel.pipeline().addLast(new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
        if (authHandler != null) {
            channel.pipeline().addLast(authHandler);
        }
        channel.pipeline().addLast(new SimpleChannelInboundHandler<HttpRequest>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
                ctx.writeAndFlush(dispatcher.dispatch(request));
            }
        });
    }

    private static ChannelHandler authHandler(final HttpServerGrouping httpParams) {
        if (httpParams.getClientAuthentication() == null) {
            return null;
        }
        final var userMap = ImmutableMap.<String, String>builder();
        httpParams.getClientAuthentication().nonnullUsers().nonnullUser().forEach(
            (ignored, user) -> {
                if (user.getAuthType() instanceof Basic basic) {
                    userMap.put(basic.requireUserId(), basic.requirePassword().getValue());
                }
            }
        );
        return new BasicAuthHandler(userMap.build());
    }
}
