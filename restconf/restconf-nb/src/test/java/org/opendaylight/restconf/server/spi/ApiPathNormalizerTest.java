/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableMap;
import java.text.ParseException;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer.Result;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint8;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link ApiPathNormalizer}.
 */
class ApiPathNormalizerTest {
    private static final QName ACTIONS_INTERFACES =
        QName.create("https://example.com/ns/example-actions", "2016-07-07", "interfaces");

    private static final ApiPathNormalizer NORMALIZER = new ApiPathNormalizer(DatabindContext.ofModel(
        YangParserTestUtils.parseYangResourceDirectory("/restconf/parser/deserializer")));

    /**
     * Test of deserialization <code>String</code> URI with container to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeContainerTest() {
        final var result = assertNormalizedPath("deserializer-test:contA").path.getPathArguments();
        assertEquals(1, result.size());
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")), result.get(0));
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing leaf to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeContainerWithLeafTest() {
        final var result = assertNormalizedPath("deserializer-test:contA/leaf-A").path.getPathArguments();
        assertEquals(2, result.size());
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")), result.get(0));
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "leaf-A")), result.get(1));
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing list with leaf list to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeContainerWithListWithLeafListTest() {
        final var result = assertNormalizedPath("deserializer-test:contA/list-A=100/leaf-list-AA=instance").path
            .getPathArguments();
        assertEquals(5, result.size());

        // container
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")), result.get(0));
        // list
        final var list = QName.create("deserializer:test", "2016-06-06", "list-A");
        assertEquals(NodeIdentifier.create(list), result.get(1));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), Uint8.valueOf(100)),
            result.get(2));
        // leaf list
        final var leafList = QName.create("deserializer:test", "2016-06-06", "leaf-list-AA");
        assertEquals(NodeIdentifier.create(leafList), result.get(3));
        assertEquals(new NodeWithValue<>(leafList, "instance"), result.get(4));
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing list with Action to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeContainerWithListWithActionTest() {
        final var result = assertNormalizedPath("example-actions:interfaces/interface=eth0/reset").path
            .getPathArguments();
        assertEquals(4, result.size());
        // container
        assertEquals(NodeIdentifier.create(ACTIONS_INTERFACES), result.get(0));
        // list
        final var list = QName.create(ACTIONS_INTERFACES, "interface");
        assertEquals(NodeIdentifier.create(list), result.get(1));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "name"), "eth0"), result.get(2));
        // action
        assertEquals(NodeIdentifier.create(QName.create(ACTIONS_INTERFACES, "reset")), result.get(3));
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing choice node with Action to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeContainerWithChoiceSchemaNodeWithActionTest() {
        final var result = assertNormalizedPath("example-actions:interfaces/typeA-gigabyte/interface=eth0/reboot").path
            .getPathArguments();
        assertEquals(6, result.size());

        // container
        assertEquals(NodeIdentifier.create(ACTIONS_INTERFACES), result.get(0));
        // choice
        assertEquals(NodeIdentifier.create(QName.create(ACTIONS_INTERFACES, "interface-type")), result.get(1));
        // container
        assertEquals(NodeIdentifier.create(QName.create(ACTIONS_INTERFACES, "typeA-gigabyte")), result.get(2));

        // list
        final var list = QName.create(ACTIONS_INTERFACES, "interface");
        assertEquals(NodeIdentifier.create(list), result.get(3));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "name"), "eth0"), result.get(4));

        // action QName
        assertEquals(NodeIdentifier.create(QName.create(ACTIONS_INTERFACES, "reboot")), result.get(5));
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing choice node with Action to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeContainerWithChoiceCaseSchemaNodeWithActionTest() {
        final var result = assertNormalizedPath("example-actions:interfaces/udp/reboot").path.getPathArguments();
        assertEquals(4, result.size());
        // container
        assertEquals(NodeIdentifier.create(ACTIONS_INTERFACES), result.get(0));
        // choice
        assertEquals(NodeIdentifier.create(QName.create(ACTIONS_INTERFACES, "protocol")), result.get(1));
        // choice container
        assertEquals(NodeIdentifier.create(QName.create(ACTIONS_INTERFACES, "udp")), result.get(2));
        // action QName
        assertEquals(NodeIdentifier.create(QName.create(ACTIONS_INTERFACES, "reboot")), result.get(3));
    }

    /**
     * Test of deserialization <code>String</code> URI containing list with no keys to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeListWithNoKeysTest() {
        final var result = assertNormalizedPath("deserializer-test:list-no-key").path.getPathArguments();
        assertEquals(2, result.size());
        final var list = QName.create("deserializer:test", "2016-06-06", "list-no-key");
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifier.create(list), result.get(1));
    }

    /**
     * Test of deserialization <code>String</code> URI containing list with one key to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeListWithOneKeyTest() {
        final var result = assertNormalizedPath("deserializer-test:list-one-key=value").path.getPathArguments();
        assertEquals(2, result.size());
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-one-key");
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "name"), "value"), result.get(1));
    }

    /**
     * Test of deserialization <code>String</code> URI containing list with multiple keys to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeListWithMultipleKeysTest() {
        final var list = QName.create("deserializer:test", "2016-06-06", "list-multiple-keys");
        final var values = ImmutableMap.<QName, Object>of(
            QName.create(list, "name"), "value",
            QName.create(list, "number"), Uint8.valueOf(100),
            QName.create(list, "enabled"), false);

        final var result = assertNormalizedPath("deserializer-test:list-multiple-keys=value,100,false").path
            .getPathArguments();
        assertEquals(2, result.size());
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, values), result.get(1));
    }

    /**
     * Test of deserialization <code>String</code> URI containing leaf list to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    void deserializeLeafListTest() {
        final var result = assertNormalizedPath("deserializer-test:leaf-list-0=true").path.getPathArguments();
        assertEquals(2, result.size());

        final QName leafList = QName.create("deserializer:test", "2016-06-06", "leaf-list-0");
        assertEquals(new NodeIdentifier(leafList), result.get(0));
        assertEquals(new NodeWithValue<>(leafList, true), result.get(1));
    }

    /**
     * Test when empty <code>String</code> is supplied as an input. Test is expected to return empty result.
     */
    @Test
    void deserializeEmptyDataTest() {
        assertEquals(YangInstanceIdentifier.of(), assertNormalizedPath("").path);
    }

    /**
     * Negative test when supplied <code>String</code> data to deserialize is null.
     */
    @Test
    void nullDataNegativeNegativeTest() {
        assertThrows(NullPointerException.class, () -> assertNormalizedPath(null));
    }

    /**
     * Negative test of creating <code>QName</code> when it is not possible to find module for specified prefix. Test is
     * expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    void prepareQnameNotExistingPrefixNegativeTest() {
        final var error = assertErrorPath("not-existing:contA");
        assertEquals("Failed to lookup for module with name 'not-existing'.", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, error.getErrorTag());
    }

    /**
     * Negative test of creating <code>QName</code> when after identifier and colon there is node name of unknown
     * node in current container. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void prepareQnameNotValidContainerNameNegativeTest() {
        final var error = assertErrorPath("deserializer-test:contA/leafB");
        assertEquals("Schema for '(deserializer:test?revision=2016-06-06)leafB' not found", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    /**
     * Negative test of creating <code>QName</code> when after identifier and equals there is node name of unknown
     * node in current list. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    void prepareQnameNotValidListNameNegativeTest() {
        final var error = assertErrorPath("deserializer-test:list-no-key/disabled=false");
        assertEquals("Schema for '(deserializer:test?revision=2016-06-06)disabled' not found", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    /**
     * Negative test of getting next identifier when current node is keyed entry. Test is expected to
     * fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    void prepareIdentifierNotKeyedEntryNegativeTest() {
        final var error = assertErrorPath("deserializer-test:list-one-key");
        assertEquals("""
            Entry '(deserializer:test?revision=2016-06-06)list-one-key' requires key or value predicate to be \
            present.""", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.MISSING_ATTRIBUTE, error.getErrorTag());
    }

    /**
     * Negative test when there is a comma also after the last key. Test is expected to fail with
     * <code>RestconfDocumentedException</code>. Last comma indicates a fourth key, which is a mismatch with schema.
     */
    @Test
    void deserializeKeysEndsWithCommaTooManyNegativeTest() {
        final var error = assertErrorPath("deserializer-test:list-multiple-keys=value,100,false,");
        assertEquals("""
            Schema for (deserializer:test?revision=2016-06-06)list-multiple-keys requires 3 key values, 4 supplied""",
            error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ATTRIBUTE, error.getErrorTag());
    }

    /**
     * Negative test when there is a comma also after the last key. Test is expected to fail with
     * <code>RestconfDocumentedException</code>. Last comma indicates a third key, whose is a mismatch with schema.
     */
    @Test
    void deserializeKeysEndsWithCommaIllegalNegativeTest() {
        final var error = assertErrorPath("deserializer-test:list-multiple-keys=value,100,");
        assertEquals("Invalid value '' for (deserializer:test?revision=2016-06-06)enabled", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
        assertEquals("Invalid value '' for boolean type. Allowed values are 'true' and 'false'", error.getErrorInfo());
    }

    /**
     * Positive when not all keys of list are encoded. The missing keys should be considered to has empty
     * <code>String</code> values. Also value of next leaf must not be considered to be missing key value.
     */
    @Test
    void notAllListKeysEncodedPositiveTest() {
        final var list = QName.create("deserializer:test", "2016-06-06", "list-multiple-keys");
        final var values = ImmutableMap.<QName, Object>of(
            QName.create(list, "name"), ":foo",
            QName.create(list, "number"), Uint8.ONE,
            QName.create(list, "enabled"), false);

        final var result = assertNormalizedPath("deserializer-test:list-multiple-keys=%3Afoo,1,false/string-value")
            .path.getPathArguments();
        assertEquals(3, result.size());
        // list
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, values), result.get(1));
        // leaf
        assertEquals(new NodeIdentifier(QName.create("deserializer:test", "2016-06-06", "string-value")),
            result.get(2));
    }

    /**
     * Negative test when not all keys of list are encoded and it is not possible to consider missing keys to be empty.
     * Test is expected to fail with <code>RestconfDocumentedException</code> and error type, error tag and error
     * status code are compared to expected values.
     */
    @Test
    void notAllListKeysEncodedNegativeTest() {
        final var error = assertErrorPath("deserializer-test:list-multiple-keys=%3Afoo/string-value");
        assertEquals("""
            Schema for (deserializer:test?revision=2016-06-06)list-multiple-keys requires 3 key values, 1 supplied""",
            error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.MISSING_ATTRIBUTE, error.getErrorTag());
    }

    /**
     * Test URI with list where key value starts with, ends with or contains percent encoded characters.The encoded
     * value should be complete also with not percent-encoded parts.
     */
    @Test
    void percentEncodedKeyEndsWithNoPercentEncodedChars() {
        final var URI = "deserializer-test:list-multiple-keys=%3Afoo,1,true";
        final var result = assertNormalizedPath(URI).path;

        final var resultListKeys = assertInstanceOf(NodeIdentifierWithPredicates.class, result.getLastPathArgument())
            .entrySet().iterator();
        assertEquals(":foo", resultListKeys.next().getValue());
        assertEquals(Uint8.ONE, resultListKeys.next().getValue());
        assertEquals(true, resultListKeys.next().getValue());
    }

    /**
     * Positive test when all keys of list can be considered to be empty <code>String</code>.
     */
    @Test
    void deserializeAllKeysEmptyTest() {
        final var list = QName.create("deserializer:test", "2016-06-06", "list-multiple-keys");
        final var values = ImmutableMap.<QName, Object>of(
            QName.create(list, "name"), "",
            QName.create(list, "number"), Uint8.ZERO,
            QName.create(list, "enabled"), true);

        final var result = assertNormalizedPath("deserializer-test:list-multiple-keys=,0,true").path.getPathArguments();
        assertEquals(2, result.size());
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, values), result.get(1));
    }

    /**
     * Negative test of deserialization when for leaf list there is no specified instance value.
     * <code>RestconfDocumentedException</code> is expected and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    void leafListMissingKeyNegativeTest() {
        final var error = assertErrorPath("deserializer-test:leaf-list-0=");
        assertEquals("Invalid value '' for (deserializer:test?revision=2016-06-06)leaf-list-0",
            error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Positive test of deserialization when parts of input URI <code>String</code> are defined in another module.
     */
    @Test
    void deserializePartInOtherModuleTest() {
        final var result = assertNormalizedPath(
            "deserializer-test-included:augmented-list=100/deserializer-test:augmented-leaf").path.getPathArguments();
        assertEquals(3, result.size());

        // list
        final var list = QName.create("deserializer:test:included", "2016-06-06", "augmented-list");
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), Uint16.valueOf(100)),
            result.get(1));

        // augmented leaf
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "augmented-leaf")),
            result.get(2));
    }

    @Test
    void deserializeListInOtherModuleTest() {
        final var result = assertNormalizedPath(
            "deserializer-test-included:augmented-list=100/deserializer-test:augmenting-list=0")
            .path.getPathArguments();
        assertEquals(4, result.size());

        // list
        final var list = QName.create("deserializer:test:included", "2016-06-06", "augmented-list");
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), Uint16.valueOf(100)),
            result.get(1));

        // augmented list
        final var augList = QName.create("deserializer:test", "2016-06-06", "augmenting-list");
        assertEquals(NodeIdentifier.create(augList), result.get(2));
        assertEquals(NodeIdentifierWithPredicates.of(augList, QName.create(augList, "id"), 0), result.get(3));
    }

    /**
     * Deserialization of path that contains list entry with key which value is described by leaflef to identityref.
     */
    @Test
    void deserializePathWithIdentityrefKeyValueTest() {
        assertIdentityrefKeyValue(
            "deserializer-test-included:refs/list-with-identityref=deserializer-test%3Aderived-identity/foo");
    }

    /**
     * Identityref key value is not encoded correctly - ':' character must be encoded as '%3A'.
     */
    @Test
    void deserializePathWithInvalidIdentityrefKeyValueTest() {
        assertIdentityrefKeyValue(
            "deserializer-test-included:refs/list-with-identityref=deserializer-test:derived-identity/foo");
    }

    private static RestconfError assertErrorPath(final String path) {
        final var apiPath = assertApiPath(path);
        final var ex = assertThrows(RestconfDocumentedException.class, () -> NORMALIZER.normalizePath(apiPath));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        return errors.get(0);
    }

    private static Result assertNormalizedPath(final String path) {
        final var result = NORMALIZER.normalizePath(assertApiPath(path));
        assertNotNull(result);
        return result;
    }

    private static ApiPath assertApiPath(final String path) {
        try {
            return ApiPath.parse(path);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertIdentityrefKeyValue(final String path) {
        final var pathArgs = assertNormalizedPath(path).path.getPathArguments();
        assertEquals(4, pathArgs.size());

        assertEquals("refs", pathArgs.get(0).getNodeType().getLocalName());
        assertEquals("list-with-identityref", pathArgs.get(1).getNodeType().getLocalName());

        final var listEntryArg = assertInstanceOf(NodeIdentifierWithPredicates.class, pathArgs.get(2));
        assertEquals("list-with-identityref", listEntryArg.getNodeType().getLocalName());
        final var keys = listEntryArg.keySet();
        assertEquals(1, keys.size());
        assertEquals("id", keys.iterator().next().getLocalName());
        final var keyValue = listEntryArg.values().iterator().next();
        assertEquals(QName.create("deserializer:test", "derived-identity", Revision.of("2016-06-06")), keyValue);

        assertEquals("foo", pathArgs.get(3).getNodeType().getLocalName());
    }
}