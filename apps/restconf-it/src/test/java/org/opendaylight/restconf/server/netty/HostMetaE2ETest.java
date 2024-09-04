/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.MediaTypes;

class HostMetaE2ETest extends AbstractE2ETest {
    private static final String XRD_URI = "/.well-known/host-meta";
    private static final String JRD_URI = "/.well-known/host-meta.json";

    @Test
    void options() throws Exception {
        final var methods = Set.of("GET", "HEAD", "OPTIONS");
        assertOptions(XRD_URI, methods);
        assertOptions(JRD_URI, methods);
    }

    @Test
    void head() throws Exception {
        assertHead(XRD_URI, MediaTypes.APPLICATION_XRD_XML);
        assertHead(JRD_URI, APPLICATION_JSON);
    }

    @Test
    void readJson() throws Exception {
        assertContentJson(JRD_URI, """
            {
              "links": {
                "rel": "restconf",
                "href": "/rests"
              }
            }
            """);
    }

//    @Test
//    void readXml() throws Exception {
//        assertContentXml(XRD_URI, """
//            <?xml version="1.0" encoding="UTF-8"?>
//            <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
//                <Link rel="restconf" href="/rests"/>
//            </XRD>
//            """);
//    }

    @Test
    void hostMetaJsonOptionsTest() throws Exception {
        final var result = invokeRequest(HttpMethod.OPTIONS, "/.well-known/host-meta.json", APPLICATION_JSON);
        assertEquals(200, result.status().code());
    }

    @Test
    void hostMetaJsonHeadTest() throws Exception {
        final var result = invokeRequest(HttpMethod.HEAD, "/.well-known/host-meta.json", APPLICATION_JSON);
        assertEquals(200, result.status().code());
    }

    @Test
    void hostMetaJsonGetTest() throws Exception {
        final var result = invokeRequest(HttpMethod.GET,"/.well-known/host-meta.json", APPLICATION_JSON);
        assertEquals(200, result.status().code());
    }
}
