/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler.WWW_AUTHENTICATE_BASIC;
import static org.opendaylight.netconf.transport.http.TestUtils.basicAuthHeader;
import static org.opendaylight.netconf.transport.http.TestUtils.httpRequest;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.List;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AbstractBasicAuthHandlerTest {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$W0rd!";
    private static final String GRANTED_URI = "/granted";
    private static final String FORBIDDEN_URI = "/forbidden";
    private static final String VALID_AUTH_HEADER = basicAuthHeader(USERNAME, PASSWORD);

    private EmbeddedChannel channel;

    @BeforeEach
    void beforeEach() {
        channel = new EmbeddedChannel(
            new AbstractBasicAuthHandler<String>() {
                @Override
                protected @Nullable String authenticate(final String username, final String password) {
                    return USERNAME.equals(username) && PASSWORD.equals(password) ? "OK" : null;
                }

                @Override
                public boolean isAuthorized(final HttpRequest request, final String authn) {
                    return GRANTED_URI.equals(request.uri());
                }
            });
    }

    @ParameterizedTest
    @MethodSource
    void unauthorized(final String authHeader) {
        channel.writeInbound(GRANTED_URI, httpRequest(authHeader.isEmpty() ? null : authHeader));
        final var response = assertInstanceOf(HttpResponse.class, channel.readOutbound());
        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
        assertEquals(WWW_AUTHENTICATE_BASIC, response.headers().get(HttpHeaderNames.WWW_AUTHENTICATE));
    }

    static List<String> unauthorized() {
        return List.of("", "Basic notBase64", TestUtils.basicAuthHeader("user", "unknown"));
    }

    void forbidden() {
        channel.writeInbound(httpRequest(FORBIDDEN_URI, VALID_AUTH_HEADER));
        final var response = assertInstanceOf(HttpResponse.class, channel.readOutbound());
        assertEquals(HttpResponseStatus.FORBIDDEN, response.status());
    }

    @Test
    void authorized() {
        channel.writeInbound(httpRequest(GRANTED_URI, VALID_AUTH_HEADER));
        // non-null readInbound() means message passed auth handler
        assertNotNull(channel.readInbound());
    }
}
