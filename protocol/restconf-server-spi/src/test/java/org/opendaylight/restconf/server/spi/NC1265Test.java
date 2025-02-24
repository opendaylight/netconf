/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.ParseException;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.ErrorInfo;
import org.opendaylight.netconf.databind.ErrorMessage;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint8;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class NC1265Test {
    private static final ApiPathNormalizer NORMALIZER = new ApiPathNormalizer(DatabindContext.ofModel(
        YangParserTestUtils.parseYangResource("/nc1265.yang")));
    private static final QName FOO = QName.create("nc1265", "foo");
    private static final QName BAR = QName.create("nc1265", "bar");
    private static final QName BAZ = QName.create("nc1265", "baz");
    private static final QName KEY = QName.create("nc1265", "key");
    private static final QName XYZZY = QName.create("nc1265", "xyzzy");

    @Test
    void uintKey() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(FOO)
            .nodeWithKey(FOO, KEY, Uint8.valueOf(123))
            .build(), "nc1265:foo=123");
    }

    @Test
    void leafrefKey() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(BAZ)
            .nodeWithKey(BAZ, KEY, Uint8.valueOf(123))
            .build(), "nc1265:baz=123");
    }

    @Test
    void instanceIdentifierKey() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(BAR)
            .nodeWithKey(BAR, KEY, YangInstanceIdentifier.builder()
                .node(BAZ)
                .nodeWithKey(BAZ, KEY, Uint8.valueOf(123))
                .build())
            .build(), "nc1265:bar=%2Fnc1265:baz[key='123']");
    }

    @Test
    void unionKeyInstanceIdentifier() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(XYZZY)
            .nodeWithKey(XYZZY, KEY, YangInstanceIdentifier.builder()
                .node(BAZ)
                .nodeWithKey(BAZ, KEY, Uint8.valueOf(123))
                .build())
            .build(), "nc1265:xyzzy=%2Fnc1265:baz[key='123']");
    }

    @Test
    void unionKeyIdentityref() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(XYZZY)
            .nodeWithKey(XYZZY, KEY, QName.create("nc1265", "base-id"))
            .build(), "nc1265:xyzzy=nc1265:base-id");
    }

    @Test
    void unionKeyLeafref() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(XYZZY)
            .nodeWithKey(XYZZY, KEY, Uint8.valueOf(123))
            .build(), "nc1265:xyzzy=123");
    }

    @Test
    void unionKeyString() {
        assertNormalized(YangInstanceIdentifier.builder()
            .node(XYZZY)
            .nodeWithKey(XYZZY, KEY, ",/")
            .build(), "nc1265:xyzzy=%2C%2F");
    }

    @Test
    void noslashInstanceIdentifierKey() {
        final var error = assertError("nc1265:bar=nc1265:baz=123");
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
        assertEquals(new ErrorMessage("Invalid value 'nc1265:baz=123' for (nc1265)key"), error.message());
        assertEquals(new ErrorInfo("""
            Could not parse Instance Identifier 'nc1265:baz=123'. Offset: 0 : Reason: Identifier must start with '/'.\
            """), error.info());
    }

    @Test
    void malformedInstanceIdentifierKey() {
        final var error = assertError("nc1265:bar=%2Fnc1265:baz[key='abc']");
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
        assertEquals(new ErrorMessage("Invalid value '/nc1265:baz[key='abc']' for (nc1265)key"), error.message());
        assertEquals(new ErrorInfo("""
            Incorrect lexical representation of integer value: abc.
            An integer value can be defined as:
              - a decimal number,
              - a hexadecimal number (prefix 0x),%n  - an octal number (prefix 0).
            Signed values are allowed. Spaces between digits are NOT allowed."""), error.info());
    }

    private static void assertNormalized(final YangInstanceIdentifier expected, final String apiPath) {
        try {
            assertEquals(expected, NORMALIZER.normalizeDataPath(assertApiPath(apiPath)).instance());
        } catch (ServerException e) {
            throw new AssertionError(e);
        }
    }

    private static ServerError assertError(final String apiPath) {
        final var parsed = assertApiPath(apiPath);
        final var errors = assertThrows(ServerException.class, () -> NORMALIZER.normalizeDataPath(parsed)).errors();
        assertEquals(1, errors.size());
        return errors.get(0);
    }

    private static ApiPath assertApiPath(final String apiPath) {
        try {
            return ApiPath.parse(apiPath);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }
}
