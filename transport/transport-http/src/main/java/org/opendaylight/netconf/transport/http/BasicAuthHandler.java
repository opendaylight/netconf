/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.Crypt;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side Basic Authorization handler.
 */
final class BasicAuthHandler extends SimpleChannelInboundHandler<HttpMessage> {
    @NonNullByDefault
    private record CryptHash(String salt, String hash) {
        CryptHash {
            requireNonNull(salt);
            requireNonNull(hash);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthHandler.class);
    private static final Pattern CRYPT_HASH_PATTERN = Pattern.compile(
        // clear text, or
        "\\$0\\$.*"
        // MD5, or
        + "|\\$1\\$[a-zA-Z0-9./]{1,8}\\$[a-zA-Z0-9./]{22}"
        // SHA-256, or
        + "|\\$5\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{43}"
        // SHA-512
        + "|\\$6\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{86}");
    private static final String DEFAULT_SALT = "$5$rounds=3500$default";

    public static final String BASIC_AUTH_PREFIX = "Basic ";
    public static final int BASIC_AUTH_CUT_INDEX = BASIC_AUTH_PREFIX.length();

    private final ImmutableMap<String, CryptHash> knownHashes;

    BasicAuthHandler(final Map<String, String> userHashes) {
        if (userHashes.isEmpty()) {
            throw new IllegalArgumentException("Hashes map should not be empty");
        }

        final var mapBuilder = ImmutableMap.<String, CryptHash>builder();
        for (var entry : userHashes.entrySet()) {
            final var hash = entry.getValue();
            if (!CRYPT_HASH_PATTERN.matcher(hash).matches()) {
                throw new IllegalArgumentException("Invalid crypt hash string \"" + hash + '"');
            }
            mapBuilder.put(entry.getKey(), hash.startsWith("$0$")
                ? new CryptHash(DEFAULT_SALT, Crypt.crypt(hash.substring(3), DEFAULT_SALT))
                    : new CryptHash(hash.substring(0, hash.lastIndexOf('$')), hash));
        }
        knownHashes = mapBuilder.build();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpMessage msg) throws Exception {
        if (isAuthorized(msg.headers().get(HttpHeaderNames.AUTHORIZATION))) {
            ctx.fireChannelRead(msg);
        } else {
            ctx.writeAndFlush(new DefaultFullHttpResponse(msg.protocolVersion(), HttpResponseStatus.UNAUTHORIZED,
                Unpooled.EMPTY_BUFFER));
        }
    }

    private boolean isAuthorized(final String authHeader) {
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
        final var found = credentials.length == 2 ? knownHashes.get(credentials[0]) : null;
        return found != null && found.hash.equals(Crypt.crypt(credentials[1], found.salt));
    }
}
