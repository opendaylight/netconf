/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RestconfSessionTest {
    @Test
    void asteriskRequestOptions() {
        final var response = RestconfSession.asteriskRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(Unpooled.EMPTY_BUFFER, response.content());
        assertEquals(
            new DefaultHttpHeaders().set(HttpHeaderNames.ALLOW, "DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT"),
            response.headers());
    }

    @ParameterizedTest
    @MethodSource
    void asteriskRequestImplemented(final HttpMethod method) {
        final var response = RestconfSession.asteriskRequest(HttpVersion.HTTP_1_1, method);
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
        assertEquals(Unpooled.EMPTY_BUFFER, response.content());
        assertEquals(new DefaultHttpHeaders(), response.headers());
    }

    private static List<Arguments> asteriskRequestImplemented() {
        // CONNECT and TRACE are excluded on purpose
        return List.of(
            Arguments.of(HttpMethod.DELETE),
            Arguments.of(HttpMethod.GET),
            Arguments.of(HttpMethod.HEAD),
            Arguments.of(HttpMethod.PATCH),
            Arguments.of(HttpMethod.POST),
            Arguments.of(HttpMethod.PUT));
    }
}
