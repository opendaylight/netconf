/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler.BASIC_AUTH_PREFIX;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class TestUtils {

    private TestUtils() {
        // hidden on purpose
    }

    static String basicAuthHeader(final String username, final String password) {
        return BASIC_AUTH_PREFIX + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    static HttpRequest httpRequest(final String authHeader) {
        return httpRequest("/uri", authHeader);
    }

    static HttpRequest httpRequest(final String uri, final String authHeader) {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        if (authHeader != null) {
            request.headers().add(HttpHeaderNames.AUTHORIZATION, authHeader);
        }
        return request;
    }
}
