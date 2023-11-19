/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParseException;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link IdentifierCodec} mostly according to examples from draft-ietf-netconf-restconf-13.
 */
class IdentifierCodecTest {
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/restconf/parser");
    private static final ApiPath URI_WITH_LIST_AND_LEAF;
    private static final ApiPath URI_WITH_INT_VAL_LEAF_LIST;

    static {
        try {
            URI_WITH_LIST_AND_LEAF =
                ApiPath.parse("list-test:top/list1=%2C%27" + '"' + "%3A" + '"' + "%20%2F,,foo/list2=a,b/result");
            URI_WITH_INT_VAL_LEAF_LIST = ApiPath.parse("list-test:top/Y=4");
        } catch (ParseException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when original <code>String</code>
     * URI contains list identifier and leaf identifier.
     */
    @Test
    void codecListAndLeafTest() {
        final var dataYangII = IdentifierCodec.deserialize(URI_WITH_LIST_AND_LEAF, MODEL_CONTEXT);
        assertEquals(URI_WITH_LIST_AND_LEAF, IdentifierCodec.serialize(dataYangII, MODEL_CONTEXT));
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when original <code>String</code>
     * URI contains leaf list identifier.
     */
    @Test
    void codecLeafListTest() {
        final var dataYangII = IdentifierCodec.deserialize(URI_WITH_INT_VAL_LEAF_LIST, MODEL_CONTEXT);
        assertEquals(URI_WITH_INT_VAL_LEAF_LIST, IdentifierCodec.serialize(dataYangII, MODEL_CONTEXT));
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> when
     * <code>String</code> URI is <code>null</code>. <code>YangInstanceIdentifier.EMPTY</code> is
     * expected to be returned.
     */
    @Test
    void codecDeserializeNullTest() {
        assertEquals(YangInstanceIdentifier.of(), IdentifierCodec.deserialize(null, MODEL_CONTEXT));
    }

    /**
     * Positive test of serialization <code>YangInstanceIdentifier.EMPTY</code>. Empty <code>String</code> is
     * expected to be returned.
     */
    @Test
    void codecSerializeEmptyTest() {
        assertEquals("", IdentifierCodec.serialize(YangInstanceIdentifier.of(), MODEL_CONTEXT));
    }

    /**
     * Positive test of serialization {@link YangInstanceIdentifier#EMPTY} and deserialization of result back to
     * {@link YangInstanceIdentifier#EMPTY}.
     */
    @Test
    void codecDeserializeAndSerializeEmptyTest() throws Exception {
        final var serialized = ApiPath.parse(IdentifierCodec.serialize(YangInstanceIdentifier.of(), MODEL_CONTEXT));
        assertEquals(YangInstanceIdentifier.of(), IdentifierCodec.deserialize(serialized, MODEL_CONTEXT));
    }
}
