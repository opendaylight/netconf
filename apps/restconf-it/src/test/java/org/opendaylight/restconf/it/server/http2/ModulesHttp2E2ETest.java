/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.yangtools.yang.common.YangConstants.RFC6020_YANG_MEDIA_TYPE;
import static org.opendaylight.yangtools.yang.common.YangConstants.RFC6020_YIN_MEDIA_TYPE;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModulesHttp2E2ETest extends AbstractHttp2E2ETest {
    private static final String MODULE_URI = "/rests/modules/network-topology?revision=2013-10-21";

    @Test
    void optionsTest() throws Exception {
        assertOptions(MODULE_URI, Set.of("GET", "HEAD", "OPTIONS"));
    }

    @Test
    void headTest() throws Exception {
        assertHead(MODULE_URI, RFC6020_YIN_MEDIA_TYPE);
        assertHead(MODULE_URI, RFC6020_YANG_MEDIA_TYPE);
    }

    @Test
    void readYinTest() throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(MODULE_URI))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), RFC6020_YIN_MEDIA_TYPE)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertEquals(RFC6020_YIN_MEDIA_TYPE,
            response.headers().firstValue(HttpHeaderNames.CONTENT_TYPE.toString()).orElseThrow());
        final var content = response.body();
        // simplified content validation
        assertTrue(content.contains(
            "<module xmlns=\"urn:ietf:params:xml:ns:yang:yin:1\" name=\"network-topology\""));
    }

    @Test
    void readYangTest() throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(MODULE_URI))
            .GET()
            .header(HttpHeaderNames.ACCEPT.toString(), RFC6020_YANG_MEDIA_TYPE)
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertEquals(RFC6020_YANG_MEDIA_TYPE,
            response.headers().firstValue(HttpHeaderNames.CONTENT_TYPE.toString()).orElseThrow());
        final var content = response.body();
        // simplified content validation
        assertTrue(content.startsWith("module network-topology"));
    }
}
