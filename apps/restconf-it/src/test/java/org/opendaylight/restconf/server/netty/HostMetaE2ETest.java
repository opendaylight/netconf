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
import org.junit.jupiter.api.Test;

class HostMetaE2ETest extends AbstractE2ETest {

    @Test
    void hostMetaOptionsTest() throws Exception {
        final var result = invokeRequest(HttpMethod.OPTIONS, "/.well-known/host-meta");
        assertEquals(200, result.status().code());
    }

    @Test
    void hostMetaHeadTest() throws Exception {
        final var result = invokeRequest(HttpMethod.HEAD,"/.well-known/host-meta");
        assertEquals(200, result.status().code());
    }

    @Test
    void hostMetaGetTest() throws Exception {
        final var result = invokeRequest(HttpMethod.GET,"/.well-known/host-meta");
        assertEquals(200, result.status().code());
    }

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
