/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.parser.builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Maps;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link YangInstanceIdentifierSerializer}
 */
public class YangInstanceIdentifierSerializerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private SchemaContext schemaContext;

    @Before
    public void init() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/restconf/parser/serializer");
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

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful",
                "/serializer-test:contA", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with leaf node to
     * <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    public void serializeContainerWithLeafTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "contA"))
                .node(QName.create("serializer:test", "2016-06-06", "leaf-list-A"))
                .node(new NodeWithValue(QName.create("serializer:test", "2016-06-06", "leaf-list-A"), "value"))
                .build();

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "/serializer-test:contA/leaf-list-A=value", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with list with leaf
     * list node to <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    public void serializeContainerWithListWithLeafListTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "contA"))
                .node(QName.create("serializer:test", "2016-06-06", "leaf-A"))
                .build();

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "/serializer-test:contA/leaf-A", result);
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
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-no-key"), Maps.newHashMap())
                .build();

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "/serializer-test:list-no-key", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with one key. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    public void serializeListWithOneKeyTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                        QName.create("serializer:test", "2016-06-06", "list-one-key"), "value")
                .build();

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "/serializer-test:list-one-key=value", result);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with multiple keys. Returned <code>String</code> is compared
     * to have expected value.
     */
    @Test
    public void serializeListWithMultipleKeysTest() {
        final QName list = QName.create("serializer:test", "2016-06-06", "list-multiple-keys");
        final Map<QName, Object> values = new LinkedHashMap<>();
        values.put(QName.create(list, "name"), "value-1");
        values.put(QName.create(list, "number"), "2");
        values.put(QName.create(list, "enabled"), "true");

        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(list).nodeWithKey(list, values).build();

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "/serializer-test:list-multiple-keys=value-1,2,true", result);
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

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "/serializer-test:leaf-0", result);
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
                .node(new NodeWithValue(QName.create("serializer:test", "2016-06-06", "leaf-list-0"), "instance"))
                .build();

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "/serializer-test:leaf-list-0=instance", result);
    }

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when
     * <code>SchemaContext</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void serializeNullSchemaContextNegativeTest() {
        thrown.expect(NullPointerException.class);
        YangInstanceIdentifierSerializer.create(null, YangInstanceIdentifier.EMPTY);
    }

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void serializeNullDataNegativeTest() {
        thrown.expect(NullPointerException.class);
        YangInstanceIdentifierSerializer.create(schemaContext, null);
    }

    /**
     * Test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is empty.
     */
    @Test
    public void serializeEmptyDataTest() {
        final String result = YangInstanceIdentifierSerializer.create(schemaContext, YangInstanceIdentifier.EMPTY);
        assertEquals("Empty identifier is expected", "", result);
    }

    /**
     * Negative test when it is not possible to find child node of current node. Test is expected to fail with
     * <code>IllegalArgumentException</code> and error message is compared to expected error message.
     */
    @Test
    public void serializeChildNodeNotFoundNegativeTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "contA"))
                .node(QName.create("serializer:test", "2016-06-06", "not-existing-leaf"))
                .build();

        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierSerializer.create(schemaContext, data);
    }

    /**
     * Test to verify if all reserved characters according to rfc3986 are considered by serializer implementation to
     * be percent encoded.
     */
    @Test
    public void verifyReservedCharactersTest() {
        final char[] genDelims = { ':', '/', '?', '#', '[', ']', '@' };
        final char[] subDelims = { '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=' };

        for (final char c : genDelims) {
            assertTrue("Current character is reserved and should be percent encoded",
                    ParserBuilderConstants.Serializer.PERCENT_ENCODE_CHARS.matches(c));
        }

        for (final char c : subDelims) {
            assertTrue("Current character is reserved and should be percent encoded",
                    ParserBuilderConstants.Serializer.PERCENT_ENCODE_CHARS.matches(c));
        }
    }

    // FIXME this is bug in current implementation - it fails on null pointer exception
    /**
     * Negative test when first argument for path does not contains namespace. Test is expected to fail with
     * <code>IllegalArgumentException</code> and error message is compared to expected error message.
     */
    @Ignore //fixme
    @Test
    public void serializeMissingNamespaceNegativeTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("contA"))
                .build();

        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierSerializer.create(schemaContext, data);
    }

    /**
     * Test if URIs with percent encoded characters are correctly serialized.
     */
    @Test
    public void serializePercentEncodingTest() {
        final String value = "foo" + ":foo bar/" + "foo,bar/" + "'bar'";
        final String encoded = "foo%3Afoo%20bar%2Ffoo%2Cbar%2F%27bar%27";

        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                        QName.create("serializer:test", "2016-06-06", "list-one-key"), value)
                .build();

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "/serializer-test:list-one-key=" + encoded, result);
    }

    /**
     * Test if URIs with no percent encoded characters are correctly serialized.
     */
    @Test //fixme
    public void serializeNoPercentEncodingTest() {
        final String value = "\"foobar\"";

        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                        QName.create("serializer:test", "2016-06-06", "list-one-key"), value)
                .build();

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "/serializer-test:list-one-key=" + value, result);
    }

    /**
     * Test of serialization when nodes in input <code>YangInstanceIdentifier</code> are defined in different modules.
     */
    @Test //fixme
    public void serializeIncludedNodesSerializationTest() {
        final YangInstanceIdentifier data = YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "included-container"))
                .node(QName.create("serializer:test", "2016-06-06", "group-list"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "group-list"),
                        QName.create("serializer:test", "2016-06-06", "group-list"), "1")
                .build();

        final String result = YangInstanceIdentifierSerializer.create(schemaContext, data);
        assertEquals("Serialization not successful", "", result);
    }

}
