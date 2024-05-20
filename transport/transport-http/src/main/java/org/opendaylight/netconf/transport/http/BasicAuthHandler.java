/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.Http2Utils.copyStreamId;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.Crypt;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240316.HttpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240316.http.server.grouping.client.authentication.users.user.auth.type.Basic;
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
        """
            \\$0\\$.*\
            |\\$1\\$[a-zA-Z0-9./]{1,8}\\$[a-zA-Z0-9./]{22}\
            |\\$5\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{43}\
            |\\$6\\$(rounds=\\d+\\$)?[a-zA-Z0-9./]{1,16}\\$[a-zA-Z0-9./]{86}""");
    private static final String DEFAULT_SALT = "$5$rounds=3500$default";

    public static final String BASIC_AUTH_PREFIX = "Basic ";
    public static final int BASIC_AUTH_CUT_INDEX = BASIC_AUTH_PREFIX.length();

    private final ImmutableMap<String, CryptHash> knownHashes;

    private BasicAuthHandler(final ImmutableMap<String, CryptHash> knownHashes) {
        this.knownHashes = requireNonNull(knownHashes);
    }

    static @Nullable BasicAuthHandler ofNullable(final HttpServerGrouping httpParams) {
        if (httpParams == null) {
            return null;
        }
        final var clientAuth = httpParams.getClientAuthentication();
        if (clientAuth == null) {
            return null;
        }

        // Basic authorization handler
        final var builder = ImmutableMap.<String, CryptHash>builder();
        clientAuth.nonnullUsers().nonnullUser().forEach((ignored, user) -> {
            if (user.getAuthType() instanceof Basic basicAuth) {
                final var basic = basicAuth.nonnullBasic();
                final var hashedPassword = basic.nonnullPassword().requireHashedPassword().getValue();
                if (!CRYPT_HASH_PATTERN.matcher(hashedPassword).matches()) {
                    throw new IllegalArgumentException("Invalid crypt hash string \"" + hashedPassword + '"');
                }
                final var cryptHash = hashedPassword.startsWith("$0$")
                    ? new CryptHash(DEFAULT_SALT, Crypt.crypt(hashedPassword.substring(3), DEFAULT_SALT))
                    : new CryptHash(hashedPassword.substring(0, hashedPassword.lastIndexOf('$')), hashedPassword);
                builder.put(basic.requireUsername(), cryptHash);
            }
        });
        final var knownHashes = builder.build();
        return knownHashes.isEmpty() ? null : new BasicAuthHandler(knownHashes);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpMessage msg) throws Exception {
        if (isAuthorized(msg.headers().get(HttpHeaderNames.AUTHORIZATION))) {
            ReferenceCountUtil.retain(msg);
            ctx.fireChannelRead(msg);
        } else {
            final var error = new DefaultFullHttpResponse(msg.protocolVersion(), HttpResponseStatus.UNAUTHORIZED,
                Unpooled.EMPTY_BUFFER);
            copyStreamId(msg, error);
            ctx.writeAndFlush(error);
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
