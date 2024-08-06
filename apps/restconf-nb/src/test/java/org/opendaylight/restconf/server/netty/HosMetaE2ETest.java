/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opendaylight.restconf.api.MediaTypes.APPLICATION_XRD_XML;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class HosMetaE2ETest extends AbstractE2ETest {

    @Test
    void hostMetaOptionsTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.OPTIONS,
            "rests/host-meta",
            APPLICATION_XRD_XML,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void hostMetaHeadTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.HEAD,
            "rests/host-meta",
            APPLICATION_XRD_XML,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void hostMetaGetTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "rests/host-meta",
            APPLICATION_XRD_XML,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void hostMetaJsonOptionsTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.OPTIONS,
            "rests/host-meta.json",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void hostMetaJsonHeadTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.HEAD,
            "rests/host-meta.json",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }

    @Test
    void hostMetaJsonGetTest() throws Exception {
        final var result = invokeRequest(buildRequest(HttpMethod.GET,
            "rests/host-meta.json",
            APPLICATION_JSON,
            null));
        assertEquals(200, result.status().code());
    }
}
