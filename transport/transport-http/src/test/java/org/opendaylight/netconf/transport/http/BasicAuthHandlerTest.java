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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opendaylight.netconf.transport.http.BasicAuthHandler.BASIC_AUTH_PREFIX;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.codec.digest.Crypt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BasicAuthHandlerTest {
    private static final String USERNAME1 = "username-1";
    private static final String USERNAME2 = "username-2";
    private static final String PASSWORD1 = "pa$$W0rd!1";
    private static final String PASSWORD2 = "pa$$W0rd#2";
    private static final Map<String, String> USER_HASHES_MAP = Map.of(
        USERNAME1, "$0$" + PASSWORD1,
        USERNAME2, Crypt.crypt(PASSWORD2, "$6$rounds=4500$sha512salt"));

    private EmbeddedChannel channel;

    @BeforeEach
    void beforeEach() {
        channel = new EmbeddedChannel(new BasicAuthHandler(USER_HASHES_MAP));
    }

    @ParameterizedTest(name = "BasicAuth success: {0} password configured")
    @MethodSource("authSuccessArgs")
    void authSuccess(final String testDesc, final String username, final String password) {
        final String authHeader = authHeader(BASIC_AUTH_PREFIX, username, password);
        final var request = newHttpRequest(authHeader);
        channel.writeInbound(request);
        // nonnull read indicates the message is passed for next handler
        assertEquals(request, channel.readInbound());
    }

    private static Stream<Arguments> authSuccessArgs() {
        return Stream.of(
            // test descriptor, username, password
            Arguments.of("unencrypted", USERNAME1, PASSWORD1),
            Arguments.of("sha512 encrypted", USERNAME2, PASSWORD2));
    }

    @ParameterizedTest(name = "BasicAuth failure: {0}")
    @MethodSource("authFailureArgs")
    void authFailure(final String testDesc, final String authHeader) {
        channel.writeInbound(newHttpRequest(authHeader));
        // null indicates the request is consumed and not passed to next handler
        assertNull(channel.readInbound());
        // verify response
        final var outbound = channel.readOutbound();
        assertNotNull(outbound);
        final var response = assertInstanceOf(HttpResponse.class, outbound);
        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
    }

    private static Stream<Arguments> authFailureArgs() {
        return Stream.of(
            // test descriptor, auth header
            Arguments.of("no Authorization header", null),
            Arguments.of("Authorization header does not start with `Basic`", "Bearer ABCD+"),
            Arguments.of("Base64 decode failure", BASIC_AUTH_PREFIX + "cannot-decode-this"),
            Arguments.of("No expected username:password",
                BASIC_AUTH_PREFIX + Base64.getEncoder().encodeToString("abcd".getBytes(StandardCharsets.UTF_8))),
            Arguments.of("Unknown user", authHeader(BASIC_AUTH_PREFIX, "unknown", "user")),
            Arguments.of("Wrong password", authHeader(BASIC_AUTH_PREFIX, USERNAME1, PASSWORD2)));
    }

    private static String authHeader(final String prefix, final String username, final String password) {
        return prefix + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static HttpRequest newHttpRequest(final String authHeader) {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        if (authHeader != null) {
            request.headers().add(HttpHeaderNames.AUTHORIZATION, authHeader);
        }
        return request;
    }
}
