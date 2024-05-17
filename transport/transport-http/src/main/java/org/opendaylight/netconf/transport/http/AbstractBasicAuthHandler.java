/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.opendaylight.netconf.transport.http.Http2Utils.copyStreamId;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBasicAuthHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBasicAuthHandler.class);
    public static final String BASIC_AUTH_PREFIX = "Basic ";
    public static final int BASIC_AUTH_CUT_INDEX = BASIC_AUTH_PREFIX.length();

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest msg) throws Exception {
        if (isAuthorized(msg.uri(), msg.headers().get(HttpHeaderNames.AUTHORIZATION))) {
            ReferenceCountUtil.retain(msg);
            ctx.fireChannelRead(msg);
        } else {
            final var error = new DefaultFullHttpResponse(msg.protocolVersion(), HttpResponseStatus.UNAUTHORIZED,
                Unpooled.EMPTY_BUFFER);
            copyStreamId(msg, error);
            ctx.writeAndFlush(error);
        }
    }

    private boolean isAuthorized(final String uri, final String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BASIC_AUTH_PREFIX)) {
            LOG.debug("UNAUTHORIZED: No Authorization (Basic) header");
            return false;
        }
        final String[] credentials;
        try {
            final var decoded = Base64.getDecoder().decode(authHeader.substring(BASIC_AUTH_CUT_INDEX));
            credentials = new String(decoded, StandardCharsets.UTF_8).split(":");
        } catch (IllegalArgumentException e) {
            LOG.debug("UNAUTHORIZED: Error decoding credentials", e);
            return false;
        }
        return credentials.length == 2 && isAuthorized(uri, credentials[0], credentials[1]);
    }

    protected abstract boolean isAuthorized(String uri, String username, String password);
}
