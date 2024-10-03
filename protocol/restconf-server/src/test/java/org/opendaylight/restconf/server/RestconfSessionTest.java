/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RestconfSessionTest {
    @Test
    void asteriskRequestOptions() {
        final var response = RestconfSession.asteriskRequest(HttpVersion.HTTP_1_1, ImplementedMethod.OPTIONS);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(Unpooled.EMPTY_BUFFER, response.content());
        assertEquals(
            new DefaultHttpHeaders().set(HttpHeaderNames.ALLOW, "DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT"),
            response.headers());
    }

    @ParameterizedTest
    @MethodSource
    void asteriskRequestImplemented(final ImplementedMethod method) {
        final var response = RestconfSession.asteriskRequest(HttpVersion.HTTP_1_1, method);
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
        assertEquals(Unpooled.EMPTY_BUFFER, response.content());
        assertEquals(new DefaultHttpHeaders(), response.headers());
    }

    private static List<Arguments> asteriskRequestImplemented() {
        // CONNECT and TRACE are excluded on purpose
        return List.of(
            Arguments.of(ImplementedMethod.DELETE),
            Arguments.of(ImplementedMethod.GET),
            Arguments.of(ImplementedMethod.HEAD),
            Arguments.of(ImplementedMethod.PATCH),
            Arguments.of(ImplementedMethod.POST),
            Arguments.of(ImplementedMethod.PUT));
    }

    @ParameterizedTest
    @MethodSource
    void hostUriOfValid(final String expected, final HttpScheme scheme, final String host) throws Exception {
        assertEquals(URI.create(expected), RestconfSession.hostUriOf(scheme, host));
    }

    private static List<Arguments> hostUriOfValid() {
        return List.of(
            Arguments.of("http://foo", HttpScheme.HTTP, "foo"),
            Arguments.of("https://bar:1234", HttpScheme.HTTPS, "bar:1234"));
    }

    @Test
    void hostUriOfInvalidPort() {
        final var ex = assertThrows(URISyntaxException.class,
            () -> RestconfSession.hostUriOf(HttpScheme.HTTP, "foo:abc"));
        assertEquals("Illegal character in port number at index 11: http://foo:abc", ex.getMessage());
    }

    @Test
    void hostUriOfInvalidHostname() {
        final var ex = assertThrows(URISyntaxException.class,
            () -> RestconfSession.hostUriOf(HttpScheme.HTTP, "--"));
        assertEquals("Illegal character in hostname at index 7: http://--", ex.getMessage());
    }

    @Test
    void hostUriOfWithUser() {
        final var ex = assertThrows(URISyntaxException.class,
            () -> RestconfSession.hostUriOf(HttpScheme.HTTP, "user@host"));
        assertEquals("Illegal Host header: user@host", ex.getMessage());
    }
}
