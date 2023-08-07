/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.ParseException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.api.ApiPath.ListInstance;
import org.opendaylight.restconf.api.ApiPath.Step;
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;

class ApiPathTest {
    @Test
    void testNull() {
        assertThrows(NullPointerException.class, () -> ApiPath.parse(null));
    }

    @Test
    void testEmpty() {
        assertEquals(List.of(), parse("/"));
    }

    @Test
    void testSingleSlash() {
        final var ex = assertThrows(ParseException.class, () -> ApiPath.parseUrl("/"));
        assertEquals("Identifier may not be empty", ex.getMessage());
        assertEquals(0, ex.getErrorOffset());
    }

    @Test
    void testTrailingSlash() {
        final var ex = assertThrows(ParseException.class, () -> ApiPath.parseUrl("foo/"));
        assertEquals("Identifier may not be empty", ex.getMessage());
        assertEquals(4, ex.getErrorOffset());
    }

    @Test
    void testExample1() {
        final var path = parse("/example-top:top/list1=key1,key2,key3/list2=key4,key5/X");
        assertEquals(4, path.size());
        assertApiIdentifier(path.get(0), "example-top", "top");
        assertListInstance(path.get(1), null, "list1", "key1", "key2", "key3");
        assertListInstance(path.get(2), null, "list2", "key4", "key5");
        assertApiIdentifier(path.get(3), null, "X");
    }

    @Test
    void testExample2() {
        final var path = parse("/example-top:top/Y=instance-value");
        assertEquals(2, path.size());
        assertApiIdentifier(path.get(0), "example-top", "top");
        assertListInstance(path.get(1), null, "Y", "instance-value");
    }

    @Test
    void testExample3() {
        final var path = parse("/example-top:top/list1=%2C%27\"%3A\"%20%2F,,foo");
        assertEquals(2, path.size());
        assertApiIdentifier(path.get(0), "example-top", "top");
        assertListInstance(path.get(1), null, "list1", ",'\":\" /", "", "foo");
    }

    @Test
    void testEscapedColon() {
        final var path = parse("/foo%3Afoo");
        assertEquals(1, path.size());
        assertApiIdentifier(path.get(0), "foo", "foo");
    }

    @Test
    void nonAsciiFirstIdentifier() {
        final var ex = assertThrows(ParseException.class, () -> ApiPath.parse("a%80"));
        assertEquals("Expecting %00-%7F, not %80", ex.getMessage());
        assertEquals(1, ex.getErrorOffset());
    }

    @Test
    void nonAsciiSecondIdentifier() {
        final var ex = assertThrows(ParseException.class, () -> ApiPath.parse("foo:a%80"));
        assertEquals("Expecting %00-%7F, not %80", ex.getMessage());
        assertEquals(5, ex.getErrorOffset());
    }

    @Test
    void testIllegalEscape() {
        final var ex = assertThrows(ParseException.class, () -> ApiPath.parse("foo:foo=%41%FF%42%FF%43"));
        assertEquals("Invalid UTF-8 sequence 'A�B�C': Input length = 1", ex.getMessage());
        assertEquals(8, ex.getErrorOffset());
    }

    private static void assertApiIdentifier(final Step step, final String module, final String identifier) {
        assertInstanceOf(ApiIdentifier.class, step);
        assertEquals(module, step.module());
        assertEquals(Unqualified.of(identifier), step.identifier());
    }

    private static void assertListInstance(final Step step, final String module, final String identifier,
            final String... keyValues) {
        final var listInstance = assertInstanceOf(ListInstance.class, step);
        assertEquals(module, step.module());
        assertEquals(Unqualified.of(identifier), step.identifier());
        assertEquals(List.of(keyValues), listInstance.keyValues());
    }

    private static List<Step> parse(final String str) {
        final String toParse = str.substring(1);
        try {
            return ApiPath.parse(toParse).steps();
        } catch (ParseException e) {
            throw new AssertionError("Failed to parse \"" + toParse + "\"", e);
        }
    }
}
