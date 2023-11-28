/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link IdentifierCodec} mostly according to examples from draft-ietf-netconf-restconf-13.
 */
class IdentifierCodecTest {
    private static final DatabindContext DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResourceDirectory("/restconf/parser"));

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when original <code>String</code>
     * URI contains list identifier and leaf identifier.
     */
    @Test
    void codecListAndLeafTest() throws Exception {
        final var dataYangII = IdentifierCodec.deserialize(ApiPath.parse(
            "list-test:top/list1=%2C%27\"%3A\"%20%2F,,foo/list2=a,b/result"), DATABIND);
        assertEquals("list-test:top/list1=%2C%27\"%3A\" %2F,,foo/list2=a,b/result",
            IdentifierCodec.serialize(dataYangII, DATABIND));
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when original <code>String</code>
     * URI contains leaf list identifier.
     */
    @Test
    void codecLeafListTest() throws Exception {
        final var str = "list-test:top/Y=4";
        final var dataYangII = IdentifierCodec.deserialize(ApiPath.parse(str), DATABIND);
        assertEquals(str, IdentifierCodec.serialize(dataYangII, DATABIND));
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> when
     * <code>String</code> URI is <code>null</code>. <code>YangInstanceIdentifier.EMPTY</code> is
     * expected to be returned.
     */
    @Test
    void codecDeserializeNullTest() {
        assertEquals(YangInstanceIdentifier.of(), IdentifierCodec.deserialize(null, DATABIND));
    }

    /**
     * Positive test of serialization <code>YangInstanceIdentifier.EMPTY</code>. Empty <code>String</code> is
     * expected to be returned.
     */
    @Test
    void codecSerializeEmptyTest() {
        assertEquals("", IdentifierCodec.serialize(YangInstanceIdentifier.of(), DATABIND));
    }

    /**
     * Positive test of serialization {@link YangInstanceIdentifier#EMPTY} and deserialization of result back to
     * {@link YangInstanceIdentifier#EMPTY}.
     */
    @Test
    void codecDeserializeAndSerializeEmptyTest() throws Exception {
        final var serialized = IdentifierCodec.serialize(YangInstanceIdentifier.of(), DATABIND);
        assertEquals(YangInstanceIdentifier.of(), IdentifierCodec.deserialize(ApiPath.parse(serialized),
            DATABIND));
    }
}
