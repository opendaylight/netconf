/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.parser.builder;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.parser.IdentifierCodec;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link IdentifierCodec} mostly according to examples from draft-ietf-netconf-restconf-13
 */
public class IdentifierCodecTest {

    private static final String URI_WITH_LIST_AND_LEAF =
            "/list-test:top/list1=%2C%27" + '"' + "%3A" + '"' + "%20%2F,,foo/list2=a,b/result";
    private static final String URI_WITH_LEAF_LIST = "/list-test:top/Y=x%3Ay";

    private SchemaContext schemaContext;

    @Before
    public void init() throws Exception {
        this.schemaContext = TestRestconfUtils.loadSchemaContext("/restconf/parser");
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String/code> when original <code>String</code>
     * URI contains list identifier and leaf identifier.
     */
    @Test
    public void codecListAndLeafTest() {
        final YangInstanceIdentifier dataYangII = IdentifierCodec.deserialize(
                this.URI_WITH_LIST_AND_LEAF, this.schemaContext);
        final String serializedDataYangII = IdentifierCodec.serialize(dataYangII, this.schemaContext);

        assertEquals("Failed codec deserialization and serialization test",
                this.URI_WITH_LIST_AND_LEAF, serializedDataYangII);
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String/code> when original <code>String</code>
     * URI contains leaf list identifier.
     */
    @Test
    public void codecLeafListTest() {
        final YangInstanceIdentifier dataYangII = IdentifierCodec.deserialize(
                this.URI_WITH_LEAF_LIST, this.schemaContext);
        final String serializedDataYangII = IdentifierCodec.serialize(dataYangII, this.schemaContext);

        assertEquals("Failed codec deserialization and serialization test",
                this.URI_WITH_LEAF_LIST, serializedDataYangII);
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> when
     * <code>String</code> URI is <code>null</code>. <code>YangInstanceIdentifier.EMPTY</code> is
     * expected to be returned.
     */
    @Test
    public void codecDeserializeNullTest () {
        final YangInstanceIdentifier dataYangII = IdentifierCodec.deserialize(null, this.schemaContext);
        assertEquals("Failed codec deserialization test", YangInstanceIdentifier.EMPTY, dataYangII);
    }

    /**
     * Positive test of serialization <code>YangInstanceIdentifier.EMPTY</code>. Single slash is
     * expected to be returned.
     */
    @Test
    public void codecSerializeEmptyTest () {
        final String serialized = IdentifierCodec.serialize(YangInstanceIdentifier.EMPTY, this.schemaContext);
        assertEquals("Failed codec serialization test", "/", serialized);
    }

    /**
     * Positive test of serialization <code>YangInstanceIdentifier.EMPTY</code> and deserialization of result back to
     * <code>YangInstanceIdentifier.EMPTY</code>.
     */
    @Test
    public void codecDeserializeAndSerializeEmptyTest() {
        final String serialized = IdentifierCodec.serialize(YangInstanceIdentifier.EMPTY, this.schemaContext);
        assertEquals("Failed codec serialization and deserialization test",
                YangInstanceIdentifier.EMPTY, IdentifierCodec.deserialize(serialized, this.schemaContext));
    }
}
