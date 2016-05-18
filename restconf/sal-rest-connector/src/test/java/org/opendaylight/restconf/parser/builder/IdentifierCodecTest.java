/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.parser.builder;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.parser.IdentifierCodec;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link IdentifierCodec} according to examples from draft-ietf-netconf-restconf-13
 */
public class IdentifierCodecTest {

    private SchemaContext schemaContext;
    private static final String uriWithList =
            "/list-test:top/list1=%2C%27" + '"' + "%3A" + '"' + "%20%2F,,foo/list2=a,b/result=x";
    private static final String uriWithLeafList = "/list-test:top/Y=x%3Ay";

    @Before
    public void init() throws Exception {
        this.schemaContext = TestRestconfUtils.loadSchemaContext("/restconf/parser");
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String/code> when original <code>String</code>
     * URI contains list identifier.
     */
    @Test
    public void codecListTest() {
        final YangInstanceIdentifier dataYangII = IdentifierCodec.deserialize(this.uriWithList, this.schemaContext);
        final String serializedDataYangII = IdentifierCodec.serialize(dataYangII, this.schemaContext);

        Assert.assertEquals(this.uriWithList, serializedDataYangII);
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String/code> when original <code>String</code>
     * URI contains leaf list identifier.
     *
     * // FIXME to do in implementation: LeafList cannot be casted to List
     */
    @Test
    public void codecLeafListTest() {
        final YangInstanceIdentifier dataYangII = IdentifierCodec.deserialize(this.uriWithLeafList, this.schemaContext);
        final String serializedDataYangII = IdentifierCodec.serialize(dataYangII, this.schemaContext);

        Assert.assertEquals(this.uriWithLeafList, serializedDataYangII);
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> when
     * <code>String</code> URI is <code>null</code>.
     */
    @Test
    public void codecDeserializeRootTest () {
        final YangInstanceIdentifier dataYangII = IdentifierCodec.deserialize(null, this.schemaContext);
        Assert.assertEquals(YangInstanceIdentifier.EMPTY, dataYangII);
    }

    /**
     * Positive test of serialization <code>YangInstanceIdentifier</code> when it is <code>EMPTY</code>.
     */
    // FIXME to do in implementation: there is bug in current implementation (serializer)
    @Test
    public void codecSerializeRootTest () {
        final String serialized = IdentifierCodec.serialize(YangInstanceIdentifier.EMPTY, this.schemaContext);
        Assert.assertEquals(null, serialized);
    }
}
