/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.BasicAuthHandler.BASIC_AUTH_PREFIX;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208.password.grouping.password.type.CleartextPassword;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.client.identity.auth.type.Basic;

class ClientChannelInitializer extends ChannelInitializer<Channel> {
    private static final int MAX_HTTP_CONTENT_LENGTH = 16 * 1024;

    private final ChannelHandler authHandler;
    private final ChannelHandler dispatcherHandler;

    ClientChannelInitializer(final HttpClientGrouping httpParams, final ChannelHandler dispatcherHandler) {
        this.authHandler = authHandler(httpParams);
        this.dispatcherHandler = requireNonNull(dispatcherHandler);
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        channel.pipeline().addLast(new HttpClientCodec());
        if (authHandler != null) {
            channel.pipeline().addLast(authHandler);
        }
        channel.pipeline().addLast(new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
        channel.pipeline().addLast(dispatcherHandler);
    }

    private static ChannelHandler authHandler(final HttpClientGrouping httpParams) {
        if (httpParams == null || httpParams.getClientIdentity() == null) {
            return null;
        }
        final var authType = httpParams.getClientIdentity().getAuthType();
        if (authType instanceof Basic basicAuth) {
            final var username = basicAuth.getBasic().getUserId();
            final var password = basicAuth.getBasic().getPasswordType() instanceof CleartextPassword clearText
                ? clearText.requireCleartextPassword() : "";
            final var authHeader = BASIC_AUTH_PREFIX + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

            return new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    if (msg instanceof HttpRequest request) {
                        request.headers().set(HttpHeaders.Names.AUTHORIZATION, authHeader);
                    }
                    super.write(ctx, msg, promise);
                }
            };
        }
        return null;
    }
}
