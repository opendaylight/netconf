/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParseException;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint8;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class NC1205Test {
    private static final ApiPathNormalizer NORMALIZER = new ApiPathNormalizer(DatabindContext.ofModel(
        YangParserTestUtils.parseYangResource("/nc1205.yang")));
    private static final QName FOO = QName.create("nc1205", "foo");
    private static final QName BAR = QName.create("nc1205", "bar");
    private static final QName BAZ = QName.create("nc1205", "baz");
    private static final QName KEY = QName.create("nc1205", "key");
    private static final QName XYZZY = QName.create("nc1205", "xyzzy");

    @Test
    void testSimpleKey() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(FOO)
            .nodeWithKey(FOO, KEY, Uint8.valueOf(123))
            .build(), "nc1205:foo=123");
    }

    @Test
    void testLeafrefKey() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(BAZ)
            .nodeWithKey(BAZ, KEY, Uint8.valueOf(123))
            .build(), "nc1205:baz=123");
    }

    @Test
    void testInstanceIdentifierKey() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(BAR)
            .nodeWithKey(BAR, KEY, YangInstanceIdentifier.builder()
                .node(BAZ)
                .nodeWithKey(BAZ, KEY, Uint8.valueOf(123))
                .build())
            .build(), "nc1205:bar=%2Fnc1205:baz=123");
    }

    // /(nc1205)bar/bar[{(nc1205)key=/(nc1205)baz/baz[{(nc1205)key=123}]}]

    private static void assertNormalized(final YangInstanceIdentifier expected, final String apiPath) {
        final ApiPath parsed;
        try {
            parsed = ApiPath.parse(apiPath);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
        assertEquals(expected, NORMALIZER.normalizeDataPath(parsed).instance());
    }
}
