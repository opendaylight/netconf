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
        assertEquals(List.of(), assertApiPath("").steps());
    }

    @Test
    void testSingleSlash() {
        final var ex = assertError("/");
        assertEquals("Identifier may not be empty", ex.getMessage());
        assertEquals(0, ex.getErrorOffset());
    }

    @Test
    void testTrailingSlash() {
        final var ex = assertError("foo/");
        assertEquals("Identifier may not be empty", ex.getMessage());
        assertEquals(4, ex.getErrorOffset());
    }

    @Test
    void testExample1() {
        final var str = "example-top:top/list1=key1,key2,key3/list2=key4,key5/X";
        final var path = assertApiPath(str);
        assertEquals(str, path.toString());

        final var steps = path.steps();
        assertEquals(4, steps.size());
        assertApiIdentifier(steps.get(0), "example-top", "top");
        assertListInstance(steps.get(1), null, "list1", "key1", "key2", "key3");
        assertListInstance(steps.get(2), null, "list2", "key4", "key5");
        assertApiIdentifier(steps.get(3), null, "X");
    }

    @Test
    void testExample2() {
        final var str = "example-top:top/Y=instance-value";
        final var path = assertApiPath(str);
        assertEquals(str, path.toString());

        final var steps = path.steps();
        assertEquals(2, steps.size());
        assertApiIdentifier(steps.get(0), "example-top", "top");
        assertListInstance(steps.get(1), null, "Y", "instance-value");
    }

    @Test
    void testExample3() {
        final var path = assertApiPath("example-top:top/list1=%2C%27\"%3A\"%20%2F,,foo");
        assertEquals("example-top:top/list1=%2C%27\"%3A\" %2F,,foo", path.toString());

        final var steps = path.steps();
        assertEquals(2, steps.size());
        assertApiIdentifier(steps.get(0), "example-top", "top");
        assertListInstance(steps.get(1), null, "list1", ",'\":\" /", "", "foo");
    }

    @Test
    void testEscapedColon() {
        final var path = assertApiPath("foo%3Afoo");
        assertEquals("foo:foo", path.toString());

        final var steps = path.steps();
        assertEquals(1, steps.size());
        assertApiIdentifier(steps.get(0), "foo", "foo");
    }

    @Test
    void nonAsciiFirstIdentifier() {
        final var ex = assertError("a%80");
        assertEquals("Expecting %00-%7F, not %80", ex.getMessage());
        assertEquals(1, ex.getErrorOffset());
    }

    @Test
    void nonAsciiSecondIdentifier() {
        final var ex = assertError("foo:a%80");
        assertEquals("Expecting %00-%7F, not %80", ex.getMessage());
        assertEquals(5, ex.getErrorOffset());
    }

    @Test
    void testIllegalEscape() {
        final var ex = assertError("foo:foo=%41%FF%42%FF%43");
        assertEquals("Invalid UTF-8 sequence 'A�B�C': Input length = 1", ex.getMessage());
        assertEquals(8, ex.getErrorOffset());
    }

    /**
     * Test to verify if all reserved characters according to rfc3986 are considered by serializer implementation to
     * be percent encoded.
     */
    @Test
    void verifyReservedCharactersTest() {
        final char[] genDelims = { ':', '/', '?', '#', '[', ']', '@' };
        final char[] subDelims = { '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=' };

        for (final char ch : genDelims) {
            assertPercentEncoded(ch);
        }

        for (final char ch : subDelims) {
            assertPercentEncoded(ch);
        }
    }

    @Test
    void testEmptyToString() {
        assertEquals("", ApiPath.empty().toString());
    }

    /**
     * Negative test of creating {@code QName} when data ends after prefix and colon.
     */
    @Test
    void prepareQnameErrorParsingNegativeTest() {
        final var ex = assertError("deserializer-test:");
        assertEquals("Identifier may not be empty", ex.getMessage());
        assertEquals(18, ex.getErrorOffset());
    }

    /**
     * Negative test of creating {@code QName} when after prefix and colon there is not parseable identifier as local
     * name.
     */
    @Test
    void prepareQnameNotValidPrefixAndLocalNameNegativeTest() {
        final var ex = assertError("deserializer-test:*not-parsable-identifier");
        assertEquals("Expecting [a-zA-Z_], not '*'", ex.getMessage());
        assertEquals(18, ex.getErrorOffset());
    }

    /**
     * Negative test of creating {@code QName} when in identifier there is another sign than colon or equals.
     */
    @Test
    void prepareQnameBuildPathNegativeTest() {
        final var ex = assertError("deserializer-test*contA");
        assertEquals("Expecting [a-zA-Z_.-], not '*'", ex.getMessage());
        assertEquals(17, ex.getErrorOffset());
    }

    /**
     * Negative test when identifier is not followed by slash or equals.
     */
    @Test
    void deserializeBadCharMissingSlashOrEqualNegativeTest() {
        final var ex = assertError("deserializer-test:cont*leaf-A");
        assertEquals("Expecting [a-zA-Z_.-], not '*'", ex.getMessage());
        assertEquals(22, ex.getErrorOffset());
    }

    /**
     * Negative test of validating identifier when there is a slash after container without next identifier.
     */
    @Test
    void validArgIdentifierContainerEndsWithSlashNegativeTest() {
        final var ex = assertError("deserializer-test:contA/");
        assertEquals("Identifier may not be empty", ex.getMessage());
        assertEquals(24, ex.getErrorOffset());
    }

    /**
     * Negative test of validating identifier when there is a slash after list key values without next identifier. Test
     * is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    void validArgIdentifierListEndsWithSlashLNegativeTest() {
        final var ex = assertError("deserializer-test:list-one-key=value/");
        assertEquals("Identifier may not be empty", ex.getMessage());
        assertEquals(37, ex.getErrorOffset());
    }

    /**
     * Negative test of validating identifier when there are multiple slashes after list key values without next
     * identifier. Test is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    void validArgIdentifierListEndsWithSlashesNegativeTest() {
        final var ex = assertError("deserializer-test:list-one-key=value//");
        assertEquals("Identifier may not be empty", ex.getMessage());
        assertEquals(37, ex.getErrorOffset());
    }

    /**
     * Negative test of validating identifier when there are multiple slashes after container without next identifier.
     */
    @Test
    void validArgIdentifierContainerEndsWithMultipleSlashesNegativeTest() {
        final var ex = assertError("deserializer-test:contA///");
        assertEquals("Identifier may not be empty", ex.getMessage());
        assertEquals(24, ex.getErrorOffset());
    }

    private static void assertPercentEncoded(final char ch) {
        final var str = ApiPath.PERCENT_ESCAPER.escape(String.valueOf(ch));
        assertEquals(3, str.length());
        assertEquals('%', str.charAt(0));
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

    private static ParseException assertError(final String str) {
        return assertThrows(ParseException.class, () -> ApiPath.parse(str));
    }

    private static ApiPath assertApiPath(final String str) {
        try {
            return ApiPath.parse(str);
        } catch (ParseException e) {
            throw new AssertionError("Failed to parse \"" + str + "\"", e);
        }
    }
}
