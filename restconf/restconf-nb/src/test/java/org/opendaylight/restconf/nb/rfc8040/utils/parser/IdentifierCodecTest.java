/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link IdentifierCodec} mostly according to examples from draft-ietf-netconf-restconf-13.
 */
public class IdentifierCodecTest {
    private static final String URI_WITH_LIST_AND_LEAF =
            "list-test:top/list1=%2C%27" + '"' + "%3A" + '"' + "%20%2F,,foo/list2=a,b/result";
    private static final String URI_WITH_INT_VAL_LEAF_LIST = "list-test:top/Y=4";

    private static EffectiveModelContext SCHEMA_CONTEXT;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/restconf/parser");
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when original <code>String</code>
     * URI contains list identifier and leaf identifier.
     */
    @Test
    public void codecListAndLeafTest() {
        final YangInstanceIdentifier dataYangII = IdentifierCodec.deserialize(
                IdentifierCodecTest.URI_WITH_LIST_AND_LEAF, SCHEMA_CONTEXT);
        final String serializedDataYangII = IdentifierCodec.serialize(dataYangII, SCHEMA_CONTEXT);

        assertEquals("Failed codec deserialization and serialization test",
                IdentifierCodecTest.URI_WITH_LIST_AND_LEAF, serializedDataYangII);
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when original <code>String</code>
     * URI contains leaf list identifier.
     */
    @Test
    public void codecLeafListTest() {
        final YangInstanceIdentifier dataYangII = IdentifierCodec.deserialize(
                IdentifierCodecTest.URI_WITH_INT_VAL_LEAF_LIST, SCHEMA_CONTEXT);
        final String serializedDataYangII = IdentifierCodec.serialize(dataYangII, SCHEMA_CONTEXT);

        assertEquals("Failed codec deserialization and serialization test",
                IdentifierCodecTest.URI_WITH_INT_VAL_LEAF_LIST, serializedDataYangII);
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> when
     * <code>String</code> URI is <code>null</code>. <code>YangInstanceIdentifier.EMPTY</code> is
     * expected to be returned.
     */
    @Test
    public void codecDeserializeNullTest() {
        final YangInstanceIdentifier dataYangII = IdentifierCodec.deserialize(null, SCHEMA_CONTEXT);
        assertEquals("Failed codec deserialization test", YangInstanceIdentifier.of(), dataYangII);
    }

    /**
     * Positive test of serialization <code>YangInstanceIdentifier.EMPTY</code>. Empty <code>String</code> is
     * expected to be returned.
     */
    @Test
    public void codecSerializeEmptyTest() {
        final String serialized = IdentifierCodec.serialize(YangInstanceIdentifier.of(), SCHEMA_CONTEXT);
        assertTrue("Failed codec serialization test", serialized.isEmpty());
    }

    /**
     * Positive test of serialization <code>YangInstanceIdentifier.EMPTY</code> and deserialization of result back to
     * <code>YangInstanceIdentifier.EMPTY</code>.
     */
    @Test
    public void codecDeserializeAndSerializeEmptyTest() {
        final String serialized = IdentifierCodec.serialize(YangInstanceIdentifier.of(), SCHEMA_CONTEXT);
        assertEquals("Failed codec serialization and deserialization test",
                YangInstanceIdentifier.of(), IdentifierCodec.deserialize(serialized, SCHEMA_CONTEXT));
    }
}
