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

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link YangInstanceIdentifierSerializer}
 */
public class YangInstanceIdentifierSerializerTest {

    private SchemaContext schemaContext;

    @Before
    public void init() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/restconf/parser/serializer");
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container node to
     * <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Ignore
    @Test
    public void serializeContainerTest() {}

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with leaf node to
     * <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    public void serializeContainerWithLeafTest() {}

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with list with leaf
     * list node to <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    public void serializeContainerWithListWithLeafListTest() {}

    /** FIXME this is bug in current implementation
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with no keys. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Ignore
    @Test
    public void serializeListWithNoKeysTest() {}

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with one key. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Ignore
    @Test
    public void serializeListWithOneKeyTest() {}

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with multiple keys. Returned <code>String</code> is compared
     * to have expected value.
     */
    @Ignore
    @Test
    public void serializeListWithMultipleKeysTest() {}

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains leaf node. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Ignore
    @Test
    public void serializeLeafTest() {}

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains leaf list node. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Ignore
    @Test
    public void serializeLeafListTest() {}

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when
     * <code>SchemaContext</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void serializeNullSchemaContextNegativeTest() {}

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void serializeNullDataNegativeTest() {}

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
    @Ignore
    @Test
    public void serializeChildNodeNotFoundNegativeTest() {}

    // FIXME this is bug in current implementation
    /**
     * Test to verify if all reserved characters according to rfc3986 are considered by serializer implementation to
     * be percent encoded.
     */
    @Test
    public void verifyReservedCharactersTest() {
        char[] genDelims = { ':', '/', '?', '#', '[', ']', '@' };
        char[] subDelims = { '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=' };

        for (char c : genDelims) {
            assertTrue("Current character is reserved and should be percent encoded",
                    ParserBuilderConstants.Serializer.PERCENT_ENCODE_CHARS.matches(c));
        }

        for (char c : subDelims) {
            assertTrue("Current character is reserved and should be percent encoded",
                    ParserBuilderConstants.Serializer.PERCENT_ENCODE_CHARS.matches(c));
        }
    }

    // FIXME this is bug in current implementation - it fails on null pointer exception
    /**
     * Negative test when first argument for path does not contains namespace. Test is expected to fail with
     * <code>IllegalArgumentException</code> and error message is compared to expected error message.
     */
    @Ignore
    @Test
    public void serializeMissingNamespaceNegativeTest() {}

    /**
     * Test if URIs with percent encoded characters are correctly serialized.
     */
    @Ignore
    @Test
    public void serializePercentEncodingTest() {}

    /**
     * Test if URIs with no percent encoded characters are correctly serialized.
     */
    @Ignore
    @Test
    public void serializeNoPercentEncodingTest() {}

    /**
     * Test of serialization when nodes in input <code>YangInstanceIdentifier</code> are defined in different modules.
     */
    @Ignore
    @Test
    public void serializeIncludedNodesSerializationTest() {}

}
