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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

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
}
