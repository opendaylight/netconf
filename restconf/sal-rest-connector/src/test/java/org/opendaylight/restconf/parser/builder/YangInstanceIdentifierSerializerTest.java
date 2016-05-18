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
import org.opendaylight.restconf.parser.IdentifierCodec;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link YangInstanceIdentifierSerializer}
 */
public class YangInstanceIdentifierSerializerTest {

    // FIXME - try to make without dependency on codec when implementing these tests
    private SchemaContext schemaContext;
    private YangInstanceIdentifier data;

    @Before
    public void init() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/restconf/parser");
        data = IdentifierCodec.deserialize(
                "/list-test:top/list1=%2C%27" + '"' + "%3A" + '"' + "%20%2F,,foo/list2=a,b/result=x", schemaContext
        );
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code>. Returned
     * <code>String</code> is compared to have expected value.
     */
    @Ignore
    @Test
    public void serializeTest() {
        YangInstanceIdentifierSerializer.create(schemaContext, data);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with multiple keys. Returned <code>String</code> is compared
     * to have expected value.
     */
    @Ignore
    @Test
    public void serializeMultipleKeysTest() {}

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains leaf list. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Ignore
    @Test
    public void serializeLeafListTest() {}

    /** FIXME this is bug in current implementation
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with no keys. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Ignore
    @Test
    public void serializeListWithNoKeysTest() {}

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when
     * <code>SchemaContext</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void nullSchemaContextNegativeTest() {}

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void nullDataNegativeTest() {}

    // FIXME this is bug in current implementation
    /**
     * Test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is empty. An identifier of root should be returned.
     */
    @Test
    public void emptyDataTest() {
        String result = YangInstanceIdentifierSerializer.create(schemaContext, YangInstanceIdentifier.EMPTY);
        assertEquals("Root identifier is expected", "/", result);
    }

    /**
     * Negative test when it is not possible to find child node of current node. Test is expected to fail with
     * <code>IllegalArgumentException</code> and error message is compared to expected error message.
     */
    @Ignore
    @Test
    public void childNodeNotFoundNegativeTest() {}

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
    public void missingNamespaceNegativeTest() {}

    /**
     * Test if URIs with percent encoded characters are correctly serialized.
     */
    @Ignore
    @Test
    public void percentEncodingTest() {}

    /**
     * Test if URIs with no percent encoded characters are correctly serialized.
     */
    @Ignore
    @Test
    public void noPercentEncodingTest() {}

}
