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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opendaylight.restconf.it.ProtocolVersion;

class ModulesE2ETest extends AbstractE2ETest {
    private static final String MODULE_URI = "/rests/modules/network-topology?revision=2013-10-21";

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void optionsTest(final ProtocolVersion version) throws Exception {
        assertOptions(MODULE_URI, Set.of("GET", "HEAD", "OPTIONS"), version);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void headTest(final ProtocolVersion version) throws Exception {
        assertHead(MODULE_URI, RFC6020_YIN_MEDIA_TYPE, version);
        assertHead(MODULE_URI, RFC6020_YANG_MEDIA_TYPE, version);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void readYinTest(final ProtocolVersion version) throws Exception {
        final var response = invokeRequest(HttpMethod.GET, MODULE_URI, version, RFC6020_YIN_MEDIA_TYPE);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(RFC6020_YIN_MEDIA_TYPE, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        final var content = response.content().toString(StandardCharsets.UTF_8);
        // simplified content validation
        assertTrue(content.contains(
            "<module xmlns=\"urn:ietf:params:xml:ns:yang:yin:1\" name=\"network-topology\""));
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void readYangTest(final ProtocolVersion version) throws Exception {
        final var response = invokeRequest(HttpMethod.GET, MODULE_URI, version, RFC6020_YANG_MEDIA_TYPE);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(RFC6020_YANG_MEDIA_TYPE, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        final var content = response.content().toString(StandardCharsets.UTF_8);
        // simplified content validation
        assertTrue(content.startsWith("module network-topology"));
    }
}
