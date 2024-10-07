/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class SegmentPeelerTest {
    @Test
    void testEmpty() {
        final var ex = assertThrows(IllegalArgumentException.class, () -> new SegmentPeeler(""));
        assertEquals("Path must start with a '/'", ex.getMessage());
    }

    @Test
    void testNotAbsolute() {
        final var ex = assertThrows(IllegalArgumentException.class, () -> new SegmentPeeler("abc"));
        assertEquals("Path must start with a '/'", ex.getMessage());
    }

    @Test
    void testZero() {
        final var it = new SegmentPeeler("/");
        assertFalse(it.hasNext());
    }

    @Test
    void testOne() {
        final var it = new SegmentPeeler("/%2f");
        assertTrue(it.hasNext());
        assertEquals("/", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testTwo() {
        final var it = new SegmentPeeler("/a/b");
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testTrailingSlash() {
        final var it = new SegmentPeeler("/a/");
        assertEquals("a", it.next());
        assertEquals("SegmentPeeler{path=/a/, remaining=/}", it.toString());
        assertEquals("", it.next());
        assertEquals("SegmentPeeler{path=/a/, remaining=}", it.toString());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void testMidSlash() {
        final var it = new SegmentPeeler("/a//b");
        assertEquals("a", it.next());
        assertEquals("SegmentPeeler{path=/a//b, remaining=//b}", it.toString());
        assertEquals("", it.next());
        assertEquals("SegmentPeeler{path=/a//b, remaining=/b}", it.toString());
        assertEquals("b", it.next());
        assertEquals("SegmentPeeler{path=/a//b, remaining=}", it.toString());
        assertThrows(NoSuchElementException.class, it::next);
    }
}
