/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.MediaTypes;

class HostMetaHttp3E2ETest extends AbstractHttp3E2ETest {
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
        final var response = client.invoke(HttpRequest.newBuilder()
            .uri(createUri(XRD_URI))
            .GET()
            .build());

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(MediaTypes.APPLICATION_XRD_XML,
            response.headers().get(HttpHeaderNames.CONTENT_TYPE.toString()));
        assertContentXml(response, """
            <?xml version="1.0" encoding="UTF-8"?>
            <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
                <Link rel="restconf" href="/rests"/>
            </XRD>""");
    }
}
