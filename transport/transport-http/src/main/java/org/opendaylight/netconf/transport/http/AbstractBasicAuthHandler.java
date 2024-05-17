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
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Partial implementation of {@link AuthHandler} for Basic Authorization. Handles username and password extraction
 * from Authorization header. Builds response messages for UNAUTHORIZED and FORBIDDEN cases when request is not
 * authenticated or not authorized (accordingly).
 *
 * @param <T> authentication data carrier type
 */
public abstract class AbstractBasicAuthHandler<T> extends SimpleChannelInboundHandler<HttpRequest>
        implements AuthHandler<T> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractBasicAuthHandler.class);
    static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final int BASIC_AUTH_CUT_INDEX = BASIC_AUTH_PREFIX.length();

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) throws Exception {
        final T authn = authenticate(request);
        if (authn == null) {
            ctx.writeAndFlush(errorResponse(request, HttpResponseStatus.UNAUTHORIZED));

        } else if (!isAuthorized(request, authn)) {
            ctx.writeAndFlush(errorResponse(request, HttpResponseStatus.FORBIDDEN));

        } else {
            ReferenceCountUtil.retain(request);
            ctx.fireChannelRead(request);
        }
    }

    @Override
    public @Nullable T authenticate(final HttpRequest request) {
        final var authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BASIC_AUTH_PREFIX)) {
            LOG.debug("UNAUTHORIZED: request has no 'Basic' Authorization header");
            return null;
        }
        final String[] credentials;
        try {
            final var decoded = Base64.getDecoder().decode(authHeader.substring(BASIC_AUTH_CUT_INDEX));
            credentials = new String(decoded, StandardCharsets.UTF_8).split(":");
        } catch (IllegalArgumentException e) {
            LOG.debug("UNAUTHORIZED: credentials decoding failed", e);
            return null;
        }
        if (credentials.length != 2) {
            LOG.debug("UNAUTHORIZED: Basic credentials contains of {} elements, expected 2", credentials.length);
            return null;
        }
        return authenticate(credentials[0], credentials[1]);
    }

    protected abstract @Nullable T authenticate(String username, String password);

    protected HttpResponse errorResponse(final HttpRequest request, final HttpResponseStatus status) {
        final var response = new DefaultFullHttpResponse(request.protocolVersion(), status, Unpooled.EMPTY_BUFFER);
        copyStreamId(request, response);
        return response;
    }
}
