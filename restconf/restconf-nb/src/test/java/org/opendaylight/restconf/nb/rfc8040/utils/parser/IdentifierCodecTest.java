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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
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
    void codecListAndLeafTest() {
        final var dataYangII = assertNormalized("list-test:top/list1=%2C%27\"%3A\"%20%2F,,foo/list2=a,b/result");
        final var list1 = QName.create("list:test", "2016-04-29", "list1");
        final var list2 = QName.create("list:test", "2016-04-29", "list2");
        assertEquals(YangInstanceIdentifier.builder()
            .node(QName.create("list:test", "2016-04-29", "top"))
            .node(list1)
            .nodeWithKey(list1, Map.<QName, Object>of(
                QName.create(list1, "key1"), ",'\":\" /",
                QName.create(list1, "key2"), "",
                QName.create(list1, "key3"), "foo"))
            .node(list2)
            .nodeWithKey(list2, Map.<QName, Object>of(
                QName.create(list2, "key4"), "a",
                QName.create(list2, "key5"), "b"))
            .node(QName.create("list:test", "2016-04-29", "result"))
            .build(), dataYangII);
        assertEquals("list-test:top/list1=%2C%27\"%3A\" %2F,,foo/list2=a,b/result",
            IdentifierCodec.serialize(dataYangII, DATABIND));
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when original <code>String</code>
     * URI contains leaf list identifier.
     */
    @Test
    void codecLeafListTest() {
        final var str = "list-test:top/Y=4";
        final var dataYangII = assertNormalized(str);
        final var y = QName.create("list:test", "2016-04-29", "Y");
        assertEquals(YangInstanceIdentifier.builder()
            .node(QName.create("list:test", "2016-04-29", "top"))
            .node(y)
            .node(new NodeWithValue<>(y, Uint32.valueOf(4)))
            .build(), dataYangII);
        assertEquals(str, IdentifierCodec.serialize(dataYangII, DATABIND));
    }

    /**
     * Positive test of serialization of an empty {@link YangInstanceIdentifier}.
     */
    @Test
    void codecDeserializeAndSerializeEmptyTest() {
        assertEquals("", IdentifierCodec.serialize(YangInstanceIdentifier.of(), DATABIND));
    }

    private static YangInstanceIdentifier assertNormalized(final String str) {
        try {
            return new ApiPathNormalizer(DATABIND).normalizePath(ApiPath.parse(str)).path;
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }
}
