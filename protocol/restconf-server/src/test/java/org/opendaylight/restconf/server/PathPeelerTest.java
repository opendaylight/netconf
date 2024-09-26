/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class PathPeelerTest {
    @Test
    void testEmpty() {
        final var ex = assertThrows(IllegalArgumentException.class, () -> new PathPeeler(""));
        assertEquals("Path must start with a '/'", ex.getMessage());
    }

    @Test
    void testNotAbsolute() {
        final var ex = assertThrows(IllegalArgumentException.class, () -> new PathPeeler("abc"));
        assertEquals("Path must start with a '/'", ex.getMessage());
    }

    @Test
    void testZero() {
        final var it = new PathPeeler("/");
        assertFalse(it.hasNext());
    }

    @Test
    void testOne() {
        final var it = new PathPeeler("/%2f");
        assertTrue(it.hasNext());
        assertEquals("/", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testTwo() {
        final var it = new PathPeeler("/a/b");
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testTrailingSlash() {
        final var it = new PathPeeler("/a/");
        assertEquals("a", it.next());
        assertEquals("PathPeeler{path=/a/, remaining=/}", it.toString());
        assertEquals("", it.next());
        assertEquals("PathPeeler{path=/a/, remaining=}", it.toString());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void testMidSlash() {
        final var it = new PathPeeler("/a//b");
        assertEquals("a", it.next());
        assertEquals("PathPeeler{path=/a//b, remaining=//b}", it.toString());
        assertEquals("", it.next());
        assertEquals("PathPeeler{path=/a//b, remaining=/b}", it.toString());
        assertEquals("b", it.next());
        assertEquals("PathPeeler{path=/a//b, remaining=}", it.toString());
        assertThrows(NoSuchElementException.class, it::next);
    }
}
