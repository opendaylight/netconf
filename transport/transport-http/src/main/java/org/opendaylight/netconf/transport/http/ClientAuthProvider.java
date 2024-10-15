/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler.BASIC_AUTH_PREFIX;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240815.HttpClientGrouping;

/**
 * A client-side channel handler adding HTTP headers.
 */
abstract sealed class ClientAuthProvider extends ChannelOutboundHandlerAdapter {
    private static final class ClientBasicAuthProvider extends ClientAuthProvider {
        private final String authHeader;

        ClientBasicAuthProvider(final String userInfo) {
            authHeader = BASIC_AUTH_PREFIX
                + Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                throws Exception {
            if (msg instanceof HttpRequest request) {
                request.headers().set(HttpHeaderNames.AUTHORIZATION, authHeader);
            }
            super.write(ctx, msg, promise);
        }
    }

    private ClientAuthProvider() {
        // Hidden on purpose
    }

    static @Nullable ClientAuthProvider ofNullable(final HttpClientGrouping httpParams)
            throws UnsupportedConfigurationException{
        if (httpParams == null) {
            return null;
        }

        final var uri = httpParams.getUri();
        if (uri == null) {
            return null;
        }

        final URI parsed;
        try {
            parsed = new URI(uri.getValue());
        } catch (URISyntaxException e) {
            throw new UnsupportedConfigurationException("Invalid URI", e);
        }

        // https://www.rfc-editor.org/rfc/rfc9110#name-deprecation-of-userinfo-in-
        final var userInfo = parsed.getUserInfo();
        return userInfo == null || userInfo.indexOf(':') == -1 ? null
            : new ClientBasicAuthProvider(userInfo);
    }
}
