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

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link YangInstanceIdentifierSerializer}.
 */
public class YangInstanceIdentifierSerializerTest {
    // schema context with test modules
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/restconf/parser/serializer");
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container node to
     * <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    public void serializeContainerTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "contA"))
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful",
                "serializer-test:contA", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with leaf node to
     * <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    public void serializeContainerWithLeafTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "contA"))
                .node(QName.create("serializer:test", "2016-06-06", "leaf-A"))
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful", "serializer-test:contA/leaf-A", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with list with leaf
     * list node to <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    public void serializeContainerWithListWithLeafListTest() {
        final QName list = QName.create("serializer:test", "2016-06-06", "list-A");
        final QName leafList = QName.create("serializer:test", "2016-06-06", "leaf-list-AA");

        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "contA"))
                .node(list)
                .node(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), 100))
                .node(leafList)
                .node(new NodeWithValue<>(leafList, "instance"))
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful",
                "serializer-test:contA/list-A=100/leaf-list-AA=instance",
                result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with no keys. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    public void serializeListWithNoKeysTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-no-key"))
                .node(QName.create("serializer:test", "2016-06-06", "list-no-key"))
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful", "serializer-test:list-no-key", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains a keyed list, but the path argument does not specify them. Returned
     * <code>String</code> is compared to have expected value.
     */
    @Test
    public void serializeMapWithNoKeysTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"), Map.of())
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful", "serializer-test:list-one-key", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with one key. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    public void serializeMapWithOneKeyTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                        QName.create("serializer:test", "2016-06-06", "list-one-key"), "value")
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful", "serializer-test:list-one-key=value", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with multiple keys. Returned <code>String</code> is compared
     * to have expected value.
     */
    @Test
    public void serializeMapWithMultipleKeysTest() {
        final QName list = QName.create("serializer:test", "2016-06-06", "list-multiple-keys");
        final Map<QName, Object> values = new LinkedHashMap<>();
        values.put(QName.create(list, "name"), "value-1");
        values.put(QName.create(list, "number"), "2");
        values.put(QName.create(list, "enabled"), "true");

        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(list).nodeWithKey(list, values).build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful", "serializer-test:list-multiple-keys=value-1,2,true", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains leaf node. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    public void serializeLeafTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "leaf-0"))
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful", "serializer-test:leaf-0", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains leaf list node. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    public void serializeLeafListTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "leaf-list-0"))
                .node(new NodeWithValue<>(QName.create("serializer:test", "2016-06-06", "leaf-list-0"), "instance"))
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful", "serializer-test:leaf-list-0=instance", result);
    }

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when
     * <code>SchemaContext</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void serializeNullSchemaContextNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> YangInstanceIdentifierSerializer.create(null, YangInstanceIdentifier.of()));
    }

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void serializeNullDataNegativeTest() {
        assertThrows(NullPointerException.class,
            () -> YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, null));
    }

    /**
     * Test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is <code>YangInstanceIdentifier.EMPTY</code>.
     * Empty <code>String</code> is expected as a return value.
     */
    @Test
    public void serializeEmptyDataTest() {
        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, YangInstanceIdentifier.of());
        assertTrue("Empty identifier is expected", result.isEmpty());
    }

    /**
     * Negative test when it is not possible to find child node of current node. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error message is compared to expected error message.
     */
    @Test
    public void serializeChildNodeNotFoundNegativeTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "contA"))
                .node(QName.create("serializer:test", "2016-06-06", "not-existing-leaf"))
                .build();

        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data));
    }

    /**
     * Test if URIs with percent encoded characters are all correctly serialized.
     */
    @Test
    public void serializePercentEncodingTest() {
        final String value = "foo:foo bar/foo,bar/'bar'";
        final String encoded = "foo%3Afoo%20bar%2Ffoo%2Cbar%2F%27bar%27";

        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                        QName.create("serializer:test", "2016-06-06", "list-one-key"), value)
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful", "serializer-test:list-one-key=" + encoded, result);
    }

    /**
     * Test if URIs with no percent encoded characters are correctly serialized. Input should be untouched.
     */
    @Test
    public void serializeNoPercentEncodingTest() {
        final String value = "foo\"b\"bar";

        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                        QName.create("serializer:test", "2016-06-06", "list-one-key"), value)
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);
        assertEquals("Serialization not successful", "serializer-test:list-one-key=" + value, result);
    }

    /**
     * Test of serialization when nodes in input <code>YangInstanceIdentifier</code> are defined in two different
     * modules by using augmentation.
     */
    @Test
    public void serializeIncludedNodesTest() {
        final QName list = QName.create("serializer:test:included", "2016-06-06", "augmented-list");
        final QName child = QName.create("serializer:test", "2016-06-06", "augmented-leaf");

        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(list)
                .node(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), 100))
                .node(child)
                .build();

        final String result = YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data);

        assertEquals("Serialization not successful",
                "serializer-test-included:augmented-list=100/serializer-test:augmented-leaf", result);
    }

    /**
     * Test of serialization when nodes in input <code>YangInstanceIdentifier</code> are defined in two different
     * modules by using augmentation. Augmented node in data supplied for serialization has wrong namespace.
     * <code>RestconfDocumentedException</code> is expected because augmented node is defined in other module than its
     * parent and will not be found.
     */
    @Test
    public void serializeIncludedNodesSerializationTest() {
        final QName list = QName.create("serializer:test:included", "2016-06-06", "augmented-list");
        // child should has different namespace
        final QName child = QName.create("serializer:test:included", "2016-06-06", "augmented-leaf");

        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(list)
                .node(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), 100))
                .node(child)
                .build();

        assertThrows(RestconfDocumentedException.class,
            () -> YangInstanceIdentifierSerializer.create(SCHEMA_CONTEXT, data));
    }
}
