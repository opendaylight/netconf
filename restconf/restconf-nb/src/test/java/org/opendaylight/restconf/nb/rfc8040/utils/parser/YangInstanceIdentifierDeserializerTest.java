/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
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
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link YangInstanceIdentifierDeserializer}.
 */
public class YangInstanceIdentifierDeserializerTest {
    private static final QName ACTIONS_INTERFACES =
        QName.create("https://example.com/ns/example-actions", "2016-07-07", "interfaces");

    // schema context
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/restconf/parser/deserializer");
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    /**
     * Test of deserialization <code>String</code> URI with container to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    public void deserializeContainerTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA")
            .path.getPathArguments();
        assertEquals(1, result.size());
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")), result.get(0));
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing leaf to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    public void deserializeContainerWithLeafTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA/leaf-A")
            .path.getPathArguments();
        assertEquals(2, result.size());
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")), result.get(0));
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "leaf-A")), result.get(1));
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing list with leaf list to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    public void deserializeContainerWithListWithLeafListTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:contA/list-A=100/leaf-list-AA=instance").path.getPathArguments();
        assertEquals(5, result.size());

        // container
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")), result.get(0));
        // list
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-A");
        assertEquals(NodeIdentifier.create(list), result.get(1));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), Uint8.valueOf(100)),
            result.get(2));
        // leaf list
        final QName leafList = QName.create("deserializer:test", "2016-06-06", "leaf-list-AA");
        assertEquals(NodeIdentifier.create(leafList), result.get(3));
        assertEquals(new NodeWithValue<>(leafList, "instance"), result.get(4));
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing list with Action to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    public void deserializeContainerWithListWithActionTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "example-actions:interfaces/interface=eth0/reset").path.getPathArguments();
        assertEquals(4, result.size());
        // container
        assertEquals(NodeIdentifier.create(ACTIONS_INTERFACES), result.get(0));
        // list
        final QName list = QName.create(ACTIONS_INTERFACES, "interface");
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
    public void deserializeContainerWithChoiceSchemaNodeWithActionTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "example-actions:interfaces/typeA-gigabyte/interface=eth0/reboot").path.getPathArguments();
        assertEquals(6, result.size());

        // container
        assertEquals(NodeIdentifier.create(ACTIONS_INTERFACES), result.get(0));
        // choice
        assertEquals(NodeIdentifier.create(QName.create(ACTIONS_INTERFACES, "interface-type")), result.get(1));
        // container
        assertEquals(NodeIdentifier.create(QName.create(ACTIONS_INTERFACES, "typeA-gigabyte")), result.get(2));

        // list
        final QName list = QName.create(ACTIONS_INTERFACES, "interface");
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
    public void deserializeContainerWithChoiceCaseSchemaNodeWithActionTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "example-actions:interfaces/udp/reboot").path.getPathArguments();
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
    public void deserializeListWithNoKeysTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:list-no-key")
            .path.getPathArguments();
        assertEquals(2, result.size());
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-no-key");
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifier.create(list), result.get(1));
    }

    /**
     * Test of deserialization <code>String</code> URI containing list with one key to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    public void deserializeListWithOneKeyTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:list-one-key=value").path.getPathArguments();
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
    public void deserializeListWithMultipleKeysTest() {
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-multiple-keys");
        final Map<QName, Object> values = ImmutableMap.of(
            QName.create(list, "name"), "value",
            QName.create(list, "number"), Uint8.valueOf(100),
            QName.create(list, "enabled"), false);

        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:list-multiple-keys=value,100,false").path.getPathArguments();
        assertEquals(2, result.size());
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, values), result.get(1));
    }

    /**
     * Test of deserialization <code>String</code> URI containing leaf list to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    public void deserializeLeafListTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:leaf-list-0=true").path.getPathArguments();
        assertEquals(2, result.size());

        final QName leafList = QName.create("deserializer:test", "2016-06-06", "leaf-list-0");
        assertEquals(new NodeIdentifier(leafList), result.get(0));
        assertEquals(new NodeWithValue<>(leafList, true), result.get(1));
    }

    /**
     * Test when empty <code>String</code> is supplied as an input. Test is expected to return empty result.
     */
    @Test
    public void deserializeEmptyDataTest() {
        assertEquals(List.of(), YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "").path.getPathArguments());
    }

    /**
     * Negative test when supplied <code>SchemaContext</code> is null. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void deserializeNullSchemaContextNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> YangInstanceIdentifierDeserializer.create(null, "deserializer-test:contA"));
    }

    /**
     * Negative test when supplied <code>String</code> data to deserialize is null. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void nullDataNegativeNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, (String) null));
    }

    /**
     * Negative test when identifier is not followed by slash or equals. Test is expected to fail with
     * <code>RestconfDocumentedException</code>.
     */
    @Test
    public void deserializeBadCharMissingSlashOrEqualNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:cont*leaf-A"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: malformed-message, "
            + "error-message: Invalid path 'deserializer-test:cont*leaf-A' at offset 22, "
            + "error-info: Expecting [a-zA-Z_.-], not '*']]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, errors.get(0).getErrorTag());
        assertEquals("Expecting [a-zA-Z_.-], not '*'", errors.get(0).getErrorInfo());
    }

    /**
     * Negative test of validating identifier when there is a slash after container without next identifier. Test
     * is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validArgIdentifierContainerEndsWithSlashNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA/"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: malformed-message, "
            + "error-message: Invalid path 'deserializer-test:contA/' at offset 24, "
            + "error-info: Identifier may not be empty]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, errors.get(0).getErrorTag());
        assertEquals("Identifier may not be empty", errors.get(0).getErrorInfo());
    }

    /**
     * Negative test of validating identifier when there are multiple slashes after container without next identifier.
     * Test is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validArgIdentifierContainerEndsWithMultipleSlashesNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA///"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: malformed-message, "
            + "error-message: Invalid path 'deserializer-test:contA///' at offset 24, "
            + "error-info: Identifier may not be empty]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, errors.get(0).getErrorTag());
        assertEquals("Identifier may not be empty", errors.get(0).getErrorInfo());
    }

    /**
     * Negative test of validating identifier when there is a slash after list key values without next identifier. Test
     * is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validArgIdentifierListEndsWithSlashLNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:list-one-key=value/"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: malformed-message, "
            + "error-message: Invalid path 'deserializer-test:list-one-key=value/' at offset 37, "
            + "error-info: Identifier may not be empty]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, errors.get(0).getErrorTag());
        assertEquals("Identifier may not be empty", errors.get(0).getErrorInfo());
    }

    /**
     * Negative test of validating identifier when there are multiple slashes after list key values without next
     * identifier. Test is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validArgIdentifierListEndsWithSlashesNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:list-one-key=value//"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: malformed-message, "
            + "error-message: Invalid path 'deserializer-test:list-one-key=value//' at offset 37, "
            + "error-info: Identifier may not be empty]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, errors.get(0).getErrorTag());
        assertEquals("Identifier may not be empty", errors.get(0).getErrorInfo());
    }

    /**
     * Negative test of creating <code>QName</code> when identifier is empty (example: '/'). Test is expected to fail
     * with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void prepareQnameEmptyIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "/"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: malformed-message, "
            + "error-message: Invalid path '/' at offset 0, "
            + "error-info: Identifier may not be empty]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, errors.get(0).getErrorTag());
        assertEquals("Identifier may not be empty", errors.get(0).getErrorInfo());
    }

    /**
     * Negative test of creating <code>QName</code> when in identifier there is another sign than colon or equals.
     * Test is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void prepareQnameBuildPathNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test*contA"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: malformed-message, "
            + "error-message: Invalid path 'deserializer-test*contA' at offset 17, "
            + "error-info: Expecting [a-zA-Z_.-], not '*']]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, errors.get(0).getErrorTag());
        assertEquals("Expecting [a-zA-Z_.-], not '*'", errors.get(0).getErrorInfo());
    }

    /**
     * Negative test of creating <code>QName</code> when it is not possible to find module for specified prefix. Test is
     * expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void prepareQnameNotExistingPrefixNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "not-existing:contA"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: unknown-element, "
            + "error-message: Failed to lookup for module with name 'not-existing'.]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, errors.get(0).getErrorTag());
    }

    /**
     * Negative test of creating <code>QName</code> when after prefix and colon there is not parsable identifier as
     * local name. Test is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void prepareQnameNotValidPrefixAndLocalNameNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class, () ->
            YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:*not-parsable-identifier"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: malformed-message, "
            + "error-message: Invalid path 'deserializer-test:*not-parsable-identifier' at offset 18, "
            + "error-info: Expecting [a-zA-Z_], not '*']]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, errors.get(0).getErrorTag());
        assertEquals("Expecting [a-zA-Z_], not '*'", errors.get(0).getErrorInfo());
    }

    /**
     * Negative test of creating <code>QName</code> when data ends after prefix and colon. Test is expected to fail
     * with <code>StringIndexOutOfBoundsException</code>.
     */
    @Test
    public void prepareQnameErrorParsingNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: malformed-message, "
            + "error-message: Invalid path 'deserializer-test:' at offset 18, "
            + "error-info: Identifier may not be empty]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, errors.get(0).getErrorTag());
        assertEquals("Identifier may not be empty", errors.get(0).getErrorInfo());
    }

    /**
     * Negative test of creating <code>QName</code> when after identifier and colon there is node name of unknown
     * node in current container. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void prepareQnameNotValidContainerNameNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA/leafB"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: data-missing, "
            + "error-message: Schema for '(deserializer:test?revision=2016-06-06)leafB' not found]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, errors.get(0).getErrorTag());
    }

    /**
     * Negative test of creating <code>QName</code> when after identifier and equals there is node name of unknown
     * node in current list. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void prepareQnameNotValidListNameNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
                "deserializer-test:list-no-key/disabled=false"));
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of getting next identifier when current node is keyed entry. Test is expected to
     * fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void prepareIdentifierNotKeyedEntryNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:list-one-key"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: missing-attribute, "
            + "error-message: Entry '(deserializer:test?revision=2016-06-06)list-one-key' requires key or value "
            + "predicate to be present.]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.MISSING_ATTRIBUTE, errors.get(0).getErrorTag());
    }

    /**
     * Negative test when there is a comma also after the last key. Test is expected to fail with
     * <code>RestconfDocumentedException</code>. Last comma indicates a fourth key, which is a mismatch with schema.
     */
    @Test
    public void deserializeKeysEndsWithCommaTooManyNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:list-multiple-keys=value,100,false,"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: unknown-attribute, "
            + "error-message: Schema for (deserializer:test?revision=2016-06-06)list-multiple-keys "
            + "requires 3 key values, 4 supplied]]", ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ATTRIBUTE, errors.get(0).getErrorTag());
    }

    /**
     * Negative test when there is a comma also after the last key. Test is expected to fail with
     * <code>RestconfDocumentedException</code>. Last comma indicates a third key, whose is a mismatch with schema.
     */
    @Test
    public void deserializeKeysEndsWithCommaIllegalNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:list-multiple-keys=value,100,"));
        assertEquals("errors: [RestconfError [error-type: protocol, error-tag: invalid-value, "
            + "error-message: Invalid value '' for (deserializer:test?revision=2016-06-06)enabled, "
            + "error-info: Invalid value '' for boolean type. Allowed values are 'true' and 'false']]",
            ex.getMessage());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, errors.get(0).getErrorTag());
        assertEquals("Invalid value '' for boolean type. Allowed values are 'true' and 'false'",
            errors.get(0).getErrorInfo());
    }

    /**
     * Positive when not all keys of list are encoded. The missing keys should be considered to has empty
     * <code>String</code> values. Also value of next leaf must not be considered to be missing key value.
     */
    @Test
    public void notAllListKeysEncodedPositiveTest() {
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-multiple-keys");
        final Map<QName, Object> values = ImmutableMap.of(
            QName.create(list, "name"), ":foo",
            QName.create(list, "number"), Uint8.ONE,
            QName.create(list, "enabled"), false);

        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:list-multiple-keys=%3Afoo,1,false/string-value").path.getPathArguments();
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
    public void notAllListKeysEncodedNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
                    "deserializer-test:list-multiple-keys=%3Afoo/string-value"));
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.MISSING_ATTRIBUTE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test URI with list where key value starts with, ends with or contains percent encoded characters.The encoded
     * value should be complete also with not percent-encoded parts.
     */
    @Test
    public void percentEncodedKeyEndsWithNoPercentEncodedChars() {
        final String URI = "deserializer-test:list-multiple-keys=%3Afoo,1,true";
        final YangInstanceIdentifier result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, URI).path;

        final Iterator<Entry<QName, Object>> resultListKeys =
                ((NodeIdentifierWithPredicates)result.getLastPathArgument()).entrySet().iterator();

        assertEquals(":foo", resultListKeys.next().getValue());
        assertEquals(Uint8.ONE, resultListKeys.next().getValue());
        assertEquals(true, resultListKeys.next().getValue());
    }

    /**
     * Positive test when all keys of list can be considered to be empty <code>String</code>.
     */
    @Test
    public void deserializeAllKeysEmptyTest() {
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-multiple-keys");
        final Map<QName, Object> values = ImmutableMap.of(
            QName.create(list, "name"), "",
            QName.create(list, "number"), Uint8.ZERO,
            QName.create(list, "enabled"), true);

        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:list-multiple-keys=,0,true").path.getPathArguments();
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
    public void leafListMissingKeyNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:leaf-list-0="));
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Positive test of deserialization when parts of input URI <code>String</code> are defined in another module.
     */
    @Test
    public void deserializePartInOtherModuleTest() {
        final List<PathArgument> result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test-included:augmented-list=100/deserializer-test:augmented-leaf")
            .path.getPathArguments();
        assertEquals(3, result.size());

        // list
        final QName list = QName.create("deserializer:test:included", "2016-06-06", "augmented-list");
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), Uint16.valueOf(100)),
            result.get(1));

        // augmented leaf
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "augmented-leaf")),
            result.get(2));
    }

    @Test
    public void deserializeListInOtherModuleTest() {
        final List<PathArgument> result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test-included:augmented-list=100/deserializer-test:augmenting-list=0")
            .path.getPathArguments();
        assertEquals(4, result.size());

        // list
        final QName list = QName.create("deserializer:test:included", "2016-06-06", "augmented-list");
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), Uint16.valueOf(100)),
            result.get(1));

        // augmented list
        final QName augList = QName.create("deserializer:test", "2016-06-06", "augmenting-list");
        assertEquals(NodeIdentifier.create(augList), result.get(2));
        assertEquals(NodeIdentifierWithPredicates.of(augList, QName.create(augList, "id"), 0), result.get(3));
    }

    /**
     * Deserialization of path that contains list entry with key which value is described by leaflef to identityref.
     */
    @Test
    public void deserializePathWithIdentityrefKeyValueTest() {
        assertIdentityrefKeyValue(
            "deserializer-test-included:refs/list-with-identityref=deserializer-test%3Aderived-identity/foo");
    }

    /**
     * Identityref key value is not encoded correctly - ':' character must be encoded as '%3A'.
     */
    @Test
    public void deserializePathWithInvalidIdentityrefKeyValueTest() {
        assertIdentityrefKeyValue(
            "deserializer-test-included:refs/list-with-identityref=deserializer-test:derived-identity/foo");
    }

    private static void assertIdentityrefKeyValue(final String path) {
        final var pathArgs = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, path).path.getPathArguments();
        assertEquals(4, pathArgs.size());

        assertEquals("refs", pathArgs.get(0).getNodeType().getLocalName());
        assertEquals("list-with-identityref", pathArgs.get(1).getNodeType().getLocalName());

        final PathArgument listEntryArg = pathArgs.get(2);
        assertThat(listEntryArg, instanceOf(NodeIdentifierWithPredicates.class));
        assertEquals("list-with-identityref", listEntryArg.getNodeType().getLocalName());
        final Set<QName> keys = ((NodeIdentifierWithPredicates) listEntryArg).keySet();
        assertEquals(1, keys.size());
        assertEquals("id", keys.iterator().next().getLocalName());
        final Object keyValue = ((NodeIdentifierWithPredicates) listEntryArg).values().iterator().next();
        assertEquals(QName.create("deserializer:test", "derived-identity", Revision.of("2016-06-06")), keyValue);

        assertEquals("foo", pathArgs.get(3).getNodeType().getLocalName());
    }
}