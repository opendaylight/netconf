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
import static org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler.BASIC_AUTH_PREFIX;
import static org.opendaylight.netconf.transport.http.TestUtils.basicAuthHeader;
import static org.opendaylight.netconf.transport.http.TestUtils.httpRequest;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;
import org.apache.commons.codec.digest.Crypt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.client.authentication.users.UserBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.client.authentication.users.user.auth.type.basic.BasicBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.client.authentication.users.user.auth.type.basic.basic.PasswordBuilder;
import org.opendaylight.yangtools.binding.util.BindingMap;

class BasicAuthHandlerTest {
    private static final String USERNAME1 = "username-1";
    private static final String USERNAME2 = "username-2";
    private static final String PASSWORD1 = "pa$$W0rd!1";
    private static final String PASSWORD2 = "pa$$W0rd#2";
    private static final String HASHED_PASSWORD2 = Crypt.crypt(PASSWORD2, "$6$rounds=4500$sha512salt");

    private EmbeddedChannel channel;

    @BeforeEach
    void beforeEach() {
        final var authHandler = BasicAuthHandlerFactory.ofNullable(new HttpServerGrouping() {
            @Override
            public Class<? extends HttpServerGrouping> implementedInterface() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getServerName() {
                return null;
            }

            @Override
            public ClientAuthentication getClientAuthentication() {
                final var user1 = new UserBuilder()
                    .setUserId(USERNAME1)
                    .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                            .http.server.grouping.client.authentication.users.user.auth.type.BasicBuilder()
                        .setBasic(new BasicBuilder()
                            .setUsername(USERNAME1)
                            .setPassword(new PasswordBuilder()
                                .setHashedPassword(new CryptHash("$0$" + PASSWORD1))
                                .build())
                            .build())
                        .build())
                    .build();
                final var user2 = new UserBuilder()
                    .setUserId(USERNAME2)
                    .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                            .http.server.grouping.client.authentication.users.user.auth.type.BasicBuilder()
                        .setBasic(new BasicBuilder()
                            .setUsername(USERNAME2)
                            .setPassword(new PasswordBuilder()
                                .setHashedPassword(new CryptHash(HASHED_PASSWORD2))
                                .build())
                            .build())
                        .build())
                    .build();

                return new ClientAuthenticationBuilder()
                    .setUsers(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                        .http.server.grouping.client.authentication.UsersBuilder()
                        .setUser(BindingMap.of(user1, user2)).build()).build();
            }
        });
        assertNotNull(authHandler);

        channel = new EmbeddedChannel(authHandler.create());
    }

    @ParameterizedTest(name = "BasicAuth success: {0} password configured")
    @MethodSource("authSuccessArgs")
    void authSuccess(final String testDesc, final String username, final String password) {
        final String authHeader = basicAuthHeader(username, password);
        final var request = httpRequest(authHeader);
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
        channel.writeInbound(httpRequest(authHeader));
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
            Arguments.of("Unknown user", basicAuthHeader("unknown", "user")),
            Arguments.of("Wrong password", basicAuthHeader(USERNAME1, PASSWORD2)));
    }
}
