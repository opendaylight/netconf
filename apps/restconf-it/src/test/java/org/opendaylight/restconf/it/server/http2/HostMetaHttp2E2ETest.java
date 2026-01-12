/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.MediaTypes;

class HostMetaHttp2E2ETest extends AbstractHttp2E2ETest {
    private static final String XRD_URI = "/.well-known/host-meta";
    private static final String JRD_URI = "/.well-known/host-meta.json";

    @Test
    void optionsTest() throws Exception {
        final var methods = Set.of("GET", "HEAD", "OPTIONS");
        assertOptions(XRD_URI, methods);
        assertOptions(JRD_URI, methods);
    }

    @Test
    void headTest() throws Exception {
        assertHead(XRD_URI, MediaTypes.APPLICATION_XRD_XML);
        assertHead(JRD_URI, APPLICATION_JSON);
    }

    @Test
    void readJsonTest() throws Exception {
        assertContentJson(JRD_URI, """
            {
              "links": [ {
                "rel": "restconf",
                "href": "/rests"
              } ]
            }
            """);
    }

    @Test
    void readXmlTest() throws Exception {
        final var response = http2Client.send(HttpRequest.newBuilder()
            .uri(createUri(XRD_URI))
            .GET()
            .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        assertEquals(MediaTypes.APPLICATION_XRD_XML,
            response.headers().firstValue(HttpHeaderNames.CONTENT_TYPE.toString()).orElseThrow());
        assertContentXml(response, """
            <?xml version="1.0" encoding="UTF-8"?>
            <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
                <Link rel="restconf" href="/rests"/>
            </XRD>""");
    }
}
