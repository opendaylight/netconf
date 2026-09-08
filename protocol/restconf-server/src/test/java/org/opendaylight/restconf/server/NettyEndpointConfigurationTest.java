/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

class NettyEndpointConfigurationTest {
    @Test
    void testEmptyPath() {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> NettyEndpointConfiguration.parsePathRootless(""));
        assertEquals("Empty path", ex.getMessage());
    }

    @Test
    void testRootPath() {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> NettyEndpointConfiguration.parsePathRootless("/"));
        assertEquals("Empty first segment", ex.getMessage());
    }

    @Test
    void testSimplePath() {
        assertEquals(List.of("a"), NettyEndpointConfiguration.parsePathRootless("a"));
        assertEquals(List.of("a", "b"), NettyEndpointConfiguration.parsePathRootless("a/b"));
        assertEquals(List.of("[", "b", "]"), NettyEndpointConfiguration.parsePathRootless("%5b/b/%5D"));
    }

    @Test
    void testBadCharacter() {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> NettyEndpointConfiguration.parsePathRootless("a/["));
        assertEquals("Invalid character '[' at offset 2", ex.getMessage());
    }

    @Test
    void testBadEncoded() {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> NettyEndpointConfiguration.parsePathRootless("x/%5X"));
        assertEquals("Cannot decode segment '%5X' at offset 2", ex.getMessage());
        final var cause = assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertEquals("invalid hex byte '5X' at index 1 of '%5X'", cause.getMessage());
    }

    @Test
    void testInvalidRequestLimits() {
        assertInvalidLimit("HTTP/1.1 request line length must be at least one byte", 0, 1, 1, 1);
        assertInvalidLimit("HTTP header size must be at least one byte", 1, 0, 1, 1);
        assertInvalidLimit("HTTP/1.1 request decoder chunk must have at least one byte", 1, 1, 0, 1);
        assertInvalidLimit("HTTP request body size must be at least one byte", 1, 1, 1, 0);
    }

    @Test
    void testRequestLimitsAffectEquality() {
        final var transport = mock(HttpServerStackGrouping.class);
        final var configuration = newConfiguration(transport, 1, 1, 1, 1);

        assertEquals(configuration, newConfiguration(transport, 1, 1, 1, 1));
        assertNotEquals(configuration, newConfiguration(transport, 2, 1, 1, 1));
        assertNotEquals(configuration, newConfiguration(transport, 1, 2, 1, 1));
        assertNotEquals(configuration, newConfiguration(transport, 1, 1, 2, 1));
        assertNotEquals(configuration, newConfiguration(transport, 1, 1, 1, 2));
    }

    private static void assertInvalidLimit(final String message, final int maxInitialLineLength,
            final int maxHeaderSize, final int maxRequestChunkSize, final int maxRequestBodySize) {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> newConfiguration(mock(HttpServerStackGrouping.class), maxInitialLineLength, maxHeaderSize,
                maxRequestChunkSize, maxRequestBodySize));
        assertEquals(message, ex.getMessage());
    }

    private static NettyEndpointConfiguration newConfiguration(final HttpServerStackGrouping transport,
            final int maxInitialLineLength, final int maxHeaderSize, final int maxRequestChunkSize,
            final int maxRequestBodySize) {
        return new NettyEndpointConfiguration(ErrorTagMapping.RFC8040, PrettyPrintParam.TRUE, Uint16.ZERO,
            Uint32.valueOf(10_000), "restconf", MessageEncoding.JSON, transport, Uint32.ONE,
            Uint32.valueOf(16384), maxInitialLineLength, maxHeaderSize, maxRequestChunkSize, maxRequestBodySize,
            Uint32.ZERO, Uint32.ZERO);
    }
}
