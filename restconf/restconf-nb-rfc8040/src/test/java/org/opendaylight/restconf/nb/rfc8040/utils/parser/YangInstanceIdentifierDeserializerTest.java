/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint8;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
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
    // schema context
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @BeforeClass
    public static void beforeClass() throws FileNotFoundException {
        SCHEMA_CONTEXT =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/restconf/parser/deserializer"));
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
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA");
        assertEquals(1, result.size());
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")), result.get(0));
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing leaf to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    public void deserializeContainerWithLeafTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA/leaf-A");
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
            "deserializer-test:contA/list-A=100/leaf-list-AA=instance");
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
            "example-actions:interfaces/interface=eth0/reset");
        assertEquals(4, result.size());
        // container
        assertEquals(NodeIdentifier.create(
            QName.create("https://example.com/ns/example-actions", "2016-07-07", "interfaces")), result.get(0));
        // list
        final QName list = QName.create("https://example.com/ns/example-actions", "2016-07-07", "interface");
        assertEquals(NodeIdentifier.create(list), result.get(1));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "name"), "eth0"), result.get(2));
        // action
        assertEquals(NodeIdentifier.create(
            QName.create("https://example.com/ns/example-actions", "2016-07-07", "reset")), result.get(3));
    }

    /**
     * Test of deserialization <code>String</code> URI containing list with no keys to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    public void deserializeListWithNoKeysTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:list-no-key");
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
            "deserializer-test:list-one-key=value");
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
            "deserializer-test:list-multiple-keys=value,100,false");
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
            "deserializer-test:leaf-list-0=true");
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
        assertEquals(List.of(), YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, ""));
    }

    /**
     * Test of deserialization <code>String</code> URI with identifiers separated by multiple slashes to
     * {@code Iterable<YangInstanceIdentifier.PathArgument>}.
     */
    @Test
    public void deserializeMultipleSlashesTest() {
        final var result = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:contA////list-A=40//list-key");
        assertEquals(4, result.size());

        // container
        assertEquals(NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")), result.get(0));
        // list
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-A");
        assertEquals(NodeIdentifier.create(list), result.get(1));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), Uint8.valueOf(40)),
            result.get(2));
        // leaf
        assertEquals(new NodeIdentifier(QName.create("deserializer:test", "2016-06-06", "list-key")), result.get(3));
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
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, null));
    }

    /**
     * Negative test when identifier is not followed by slash or equals. Test is expected to fail with
     * <code>RestconfDocumentedException</code>.
     */
    @Test
    public void deserializeBadCharMissingSlashOrEqualNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:cont*leaf-A"));
    }

    /**
     * Negative test of validating identifier when there is a slash after container without next identifier. Test
     * is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validArgIdentifierContainerEndsWithSlashNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA/"));
    }

    /**
     * Negative test of validating identifier when there are multiple slashes after container without next identifier.
     * Test is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validArgIdentifierContainerEndsWithMultipleSlashesNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA///"));
    }

    /**
     * Negative test of validating identifier when there is a slash after list key values without next identifier. Test
     * is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validArgIdentifierListEndsWithSlashLNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:list-one-key=value/"));
    }

    /**
     * Negative test of validating identifier when there are multiple slashes after list key values without next
     * identifier. Test is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validArgIdentifierListEndsWithSlashesNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:list-one-key=value//"));
    }

    /**
     * Negative test of creating <code>QName</code> when identifier is empty (example: '/'). Test is expected to fail
     * with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void prepareQnameEmptyIdentifierNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "/"));
    }

    /**
     * Negative test of creating <code>QName</code> when in identifier there is another sign than colon or equals.
     * Test is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void prepareQnameBuildPathNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test*contA"));
    }

    /**
     * Negative test of creating <code>QName</code> when it is not possible to find module for specified prefix. Test is
     * expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void prepareQnameNotExistingPrefixNegativeTest() {
        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "not-existing:contA"));
    }

    /**
     * Negative test of creating <code>QName</code> when after prefix and colon there is not parsable identifier as
     * local name. Test is expected to fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    public void prepareQnameNotValidPrefixAndLocalNameNegativeTest() {
        assertThrows(RestconfDocumentedException.class, () ->
            YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:*not-parsable-identifier"));
    }

    /**
     * Negative test of creating <code>QName</code> when data ends after prefix and colon. Test is expected to fail
     * with <code>StringIndexOutOfBoundsException</code>.
     */
    @Test
    public void prepareQnameErrorParsingNegativeTest() {
        assertThrows(StringIndexOutOfBoundsException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:"));
    }

    /**
     * Negative test of creating <code>QName</code> when after identifier and colon there is node name of unknown
     * node in current container. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void prepareQnameNotValidContainerNameNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:contA/leafB"));
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of creating <code>QName</code> when after identifier and equals there is node name of unknown
     * node in current list. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void prepareQnameNotValidListNameNegativeTest() {
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
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
        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, "deserializer-test:list-one-key"));
    }

    /**
     * Negative test when there is a comma also after the last key. Test is expected to fail with
     * <code>RestconfDocumentedException</code>.
     */
    @Test
    public void deserializeKeysEndsWithComaNegativeTest() {
        assertThrows(RestconfDocumentedException.class, () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "deserializer-test:list-multiple-keys=value,100,false,"));
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
            "deserializer-test:list-multiple-keys=%3Afoo,1,false/string-value");
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
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
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
        final YangInstanceIdentifier result = YangInstanceIdentifier.create(
                YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT, URI));

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
            "deserializer-test:list-multiple-keys=,0,true");
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
        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
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
            "deserializer-test-included:augmented-list=100/augmented-leaf");

        assertEquals(4, result.size());

        final QName list = QName.create("deserializer:test:included", "2016-06-06", "augmented-list");
        final QName child = QName.create("deserializer:test", "2016-06-06", "augmented-leaf");

        // list
        assertEquals(NodeIdentifier.create(list), result.get(0));
        assertEquals(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), Uint16.valueOf(100)),
            result.get(1));

        // augmented leaf
        assertEquals(new AugmentationIdentifier(Set.of(child)), result.get(2));
        assertEquals(NodeIdentifier.create(child), result.get(3));
    }

    /**
     * Deserialization of path that contains list entry with key which value is described by leaflef to identityref.
     */
    @Test
    public void deserializePathWithIdentityrefKeyValueTest() {
        final var pathArgs = YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
                "deserializer-test-included:refs/list-with-identityref=deserializer-test%3Aderived-identity/foo");
        assertEquals(4, pathArgs.size());

        assertEquals("refs", pathArgs.get(0).getNodeType().getLocalName());
        assertEquals("list-with-identityref", pathArgs.get(1).getNodeType().getLocalName());

        final PathArgument listEntryArg = pathArgs.get(2);
        assertTrue(listEntryArg instanceof NodeIdentifierWithPredicates);
        assertEquals("list-with-identityref", listEntryArg.getNodeType().getLocalName());
        final Set<QName> keys = ((NodeIdentifierWithPredicates) listEntryArg).keySet();
        assertEquals(1, keys.size());
        assertEquals("id", keys.iterator().next().getLocalName());
        final Object keyValue = ((NodeIdentifierWithPredicates) listEntryArg).values().iterator().next();
        assertEquals(QName.create("deserializer:test", "derived-identity", Revision.of("2016-06-06")), keyValue);

        assertEquals("foo", pathArgs.get(3).getNodeType().getLocalName());
    }

    /**
     * Identityref key value is not encoded correctly - ':' character must be encoded as '%3A'.
     */
    @Test
    public void deserializePathWithInvalidIdentityrefKeyValueTest() {
        assertThrows(RestconfDocumentedException.class, () -> YangInstanceIdentifierDeserializer.create(SCHEMA_CONTEXT,
            "refs/list-with-identityref=deserializer-test:derived-identity/foo"));
    }
}