/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.yangtools.yang.common.YangConstants.RFC6020_YANG_MEDIA_TYPE;
import static org.opendaylight.yangtools.yang.common.YangConstants.RFC6020_YIN_MEDIA_TYPE;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModulesE2ETest extends AbstractE2ETest {
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
        final var response = invokeRequest(HttpMethod.GET, MODULE_URI, RFC6020_YIN_MEDIA_TYPE);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(RFC6020_YIN_MEDIA_TYPE, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        final var content = response.content().toString(StandardCharsets.UTF_8);
        // simplified content validation
        assertTrue(content.contains(
            "<module xmlns=\"urn:ietf:params:xml:ns:yang:yin:1\" name=\"network-topology\""));
    }

    @Test
    void readYangTest() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, MODULE_URI, RFC6020_YANG_MEDIA_TYPE);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(RFC6020_YANG_MEDIA_TYPE, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        final var content = response.content().toString(StandardCharsets.UTF_8);
        // simplified content validation
        assertTrue(content.startsWith("module network-topology"));
    }
}
