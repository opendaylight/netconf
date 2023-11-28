/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link YangInstanceIdentifierSerializer}.
 */
class YangInstanceIdentifierSerializerTest {
    private static final DatabindContext DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResourceDirectory("/restconf/parser/serializer"));

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container node to
     * <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    void serializeContainerTest() {
        assertEquals("serializer-test:contA", YangInstanceIdentifierSerializer.create(DATABIND,
            YangInstanceIdentifier.of(QName.create("serializer:test", "2016-06-06", "contA"))));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with leaf node to
     * <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    void serializeContainerWithLeafTest() {
        assertEquals("serializer-test:contA/leaf-A", YangInstanceIdentifierSerializer.create(DATABIND,
            YangInstanceIdentifier.of(
                QName.create("serializer:test", "2016-06-06", "contA"),
                QName.create("serializer:test", "2016-06-06", "leaf-A"))));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with list with leaf
     * list node to <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    void serializeContainerWithListWithLeafListTest() {
        final var list = QName.create("serializer:test", "2016-06-06", "list-A");
        final var leafList = QName.create("serializer:test", "2016-06-06", "leaf-list-AA");

        assertEquals("serializer-test:contA/list-A=100/leaf-list-AA=instance",
            YangInstanceIdentifierSerializer.create(DATABIND, YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "contA"))
                .node(list)
                .node(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), 100))
                .node(leafList)
                .node(new NodeWithValue<>(leafList, "instance"))
                .build()));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with no keys. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    void serializeListWithNoKeysTest() {
        assertEquals("serializer-test:list-no-key", YangInstanceIdentifierSerializer.create(DATABIND,
            YangInstanceIdentifier.of(
                QName.create("serializer:test", "2016-06-06", "list-no-key"),
                QName.create("serializer:test", "2016-06-06", "list-no-key"))));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains a keyed list, but the path argument does not specify them. Returned
     * <code>String</code> is compared to have expected value.
     */
    @Test
    void serializeMapWithNoKeysTest() {
        assertEquals("serializer-test:list-one-key", YangInstanceIdentifierSerializer.create(DATABIND,
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"), Map.of())
                .build()));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with one key. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    void serializeMapWithOneKeyTest() {
        assertEquals("serializer-test:list-one-key=value", YangInstanceIdentifierSerializer.create(DATABIND,
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                    QName.create("serializer:test", "2016-06-06", "list-one-key"), "value")
                .build()));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with multiple keys. Returned <code>String</code> is compared
     * to have expected value.
     */
    @Test
    void serializeMapWithMultipleKeysTest() {
        final var list = QName.create("serializer:test", "2016-06-06", "list-multiple-keys");

        assertEquals("serializer-test:list-multiple-keys=value-1,2,true",
            YangInstanceIdentifierSerializer.create(DATABIND, YangInstanceIdentifier.builder()
                .node(list)
                .nodeWithKey(list, ImmutableMap.of(
                    QName.create(list, "name"), "value-1",
                    QName.create(list, "number"), "2",
                    QName.create(list, "enabled"), "true"))
                .build()));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains leaf node. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    void serializeLeafTest() {
        assertEquals("serializer-test:leaf-0", YangInstanceIdentifierSerializer.create(DATABIND,
            YangInstanceIdentifier.of(QName.create("serializer:test", "2016-06-06", "leaf-0"))));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains leaf list node. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    void serializeLeafListTest() {
        assertEquals("serializer-test:leaf-list-0=instance", YangInstanceIdentifierSerializer.create(DATABIND,
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "leaf-list-0"))
                .node(new NodeWithValue<>(QName.create("serializer:test", "2016-06-06", "leaf-list-0"), "instance"))
                .build()));
    }

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when
     * <code>SchemaContext</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    void serializeNullSchemaContextNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> YangInstanceIdentifierSerializer.create(null, YangInstanceIdentifier.of()));
    }

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    void serializeNullDataNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> YangInstanceIdentifierSerializer.create(DATABIND, null));
    }

    /**
     * Test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is <code>YangInstanceIdentifier.EMPTY</code>.
     * Empty <code>String</code> is expected as a return value.
     */
    @Test
    void serializeEmptyDataTest() {
        assertEquals("", YangInstanceIdentifierSerializer.create(DATABIND, YangInstanceIdentifier.of()));
    }

    /**
     * Negative test when it is not possible to find child node of current node. Test is expected to fail with
     * {@link RestconfDocumentedException} and error message is compared to expected error message.
     */
    @Test
    void serializeChildNodeNotFoundNegativeTest() {
        final var data = YangInstanceIdentifier.of(
                QName.create("serializer:test", "2016-06-06", "contA"),
                QName.create("serializer:test", "2016-06-06", "not-existing-leaf"));

        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierSerializer.create(DATABIND, data));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("""
            Invalid input '/(serializer:test?revision=2016-06-06)contA/not-existing-leaf': schema for argument \
            '(serializer:test?revision=2016-06-06)not-existing-leaf' (after 'serializer-test:contA') not found""",
            error.getErrorMessage());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, error.getErrorTag());
    }

    /**
     * Test if URIs with percent encoded characters are all correctly serialized.
     */
    @Test
    void serializePercentEncodingTest() {
        assertEquals("serializer-test:list-one-key=foo%3Afoo bar%2Ffoo%2Cbar%2F%27bar%27",
            YangInstanceIdentifierSerializer.create(DATABIND,
                YangInstanceIdentifier.builder()
                    .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                    .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                        QName.create("serializer:test", "2016-06-06", "list-one-key"), "foo:foo bar/foo,bar/'bar'")
                    .build()));
    }

    /**
     * Test if URIs with no percent encoded characters are correctly serialized. Input should be untouched.
     */
    @Test
    void serializeNoPercentEncodingTest() {
        assertEquals("serializer-test:list-one-key=foo\"b\"bar", YangInstanceIdentifierSerializer.create(DATABIND,
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                    QName.create("serializer:test", "2016-06-06", "list-one-key"), "foo\"b\"bar")
            .build()));
    }

    /**
     * Test of serialization when nodes in input <code>YangInstanceIdentifier</code> are defined in two different
     * modules by using augmentation.
     */
    @Test
    void serializeIncludedNodesTest() {
        final var list = QName.create("serializer:test:included", "2016-06-06", "augmented-list");
        final var child = QName.create("serializer:test", "2016-06-06", "augmented-leaf");

        assertEquals("serializer-test-included:augmented-list=100/serializer-test:augmented-leaf",
            YangInstanceIdentifierSerializer.create(DATABIND, YangInstanceIdentifier.builder()
                .node(list)
                .node(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), 100))
                .node(child)
                .build()));
    }

    /**
     * Test of serialization when nodes in input <code>YangInstanceIdentifier</code> are defined in two different
     * modules by using augmentation. Augmented node in data supplied for serialization has wrong namespace.
     * <code>RestconfDocumentedException</code> is expected because augmented node is defined in other module than its
     * parent and will not be found.
     */
    @Test
    void serializeIncludedNodesSerializationTest() {
        final var list = QName.create("serializer:test:included", "2016-06-06", "augmented-list");

        final var data = YangInstanceIdentifier.builder()
                .node(list)
                .node(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), 100))
                // child should has different namespace
                .node(QName.create("serializer:test:included", "2016-06-06", "augmented-leaf"))
                .build();

        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierSerializer.create(DATABIND, data));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("""
            Invalid input '/(serializer:test:included?revision=2016-06-06)augmented-list/augmented-list[{(\
            serializer:test:included?revision=2016-06-06)list-key=100}]/augmented-leaf': schema for argument \
            '(serializer:test:included?revision=2016-06-06)augmented-leaf' (after \
            'serializer-test-included:augmented-list=100') not found""", error.getErrorMessage());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, error.getErrorTag());
    }
}
