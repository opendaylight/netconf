/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtocolCapabilityTest {
    @Test
    void forUnknownURN() {
        assertNull(SimpleCapability.forURN("tweedly dee"));
    }

    @Test
    void forNullURN() {
        assertThrows(NullPointerException.class, () -> SimpleCapability.forURN(null));
    }

    @Test
    void ofUnknownURN() {
        assertThrows(IllegalArgumentException.class, () -> SimpleCapability.ofURN("tweedly dum"));
        assertThrows(NullPointerException.class, () -> SimpleCapability.ofURN(null));
    }

    @ParameterizedTest
    @MethodSource("protocolCapabilities")
    void forURN(final String urn, final SimpleCapability expected) {
        assertSame(expected, SimpleCapability.forURN(urn));
    }

    static Stream<Arguments> protocolCapabilities() {
        return Stream.of(
            arguments("urn:ietf:params:netconf:base:1.0", SimpleCapability.BASE),
            arguments("urn:ietf:params:netconf:base:1.1", SimpleCapability.BASE_1_1),
            arguments("urn:ietf:params:netconf:capability:candidate:1.0", SimpleCapability.CANDIDATE),
            arguments("urn:ietf:params:netconf:capability:confirmed-commit:1.0", SimpleCapability.CONFIRMED_COMMIT),
            arguments("urn:ietf:params:netconf:capability:confirmed-commit:1.1",
                SimpleCapability.CONFIRMED_COMMIT_1_1),
            arguments("urn:ietf:params:netconf:capability:exi:1.0", SimpleCapability.EXI),
            arguments("urn:ietf:params:netconf:capability:interleave:1.0", SimpleCapability.INTERLEAVE),
            arguments("urn:ietf:params:netconf:capability:notification:1.0", SimpleCapability.NOTIFICATION),
            arguments("urn:ietf:params:netconf:capability:partial-lock:1.0", SimpleCapability.PARTIAL_LOCK),
            arguments("urn:ietf:params:netconf:capability:rollback-on-error:1.0", SimpleCapability.ROLLBACK_ON_ERROR),
            arguments("urn:ietf:params:netconf:capability:startup:1.0", SimpleCapability.STARTUP),
            arguments("urn:ietf:params:netconf:capability:time:1.0", SimpleCapability.TIME),
            arguments("urn:ietf:params:netconf:capability:url:1.0", SimpleCapability.URL),
            arguments("urn:ietf:params:netconf:capability:validate:1.0", SimpleCapability.VALIDATE),
            arguments("urn:ietf:params:netconf:capability:validate:1.1", SimpleCapability.VALIDATE_1_1),
            arguments("urn:ietf:params:netconf:capability:with-defaults:1.0", SimpleCapability.WITH_DEFAULTS),
            arguments("urn:ietf:params:netconf:capability:with-operational-defaults:1.0",
                SimpleCapability.WITH_OPERATIONAL_DEFAULTS),
            arguments("urn:ietf:params:netconf:capability:writable-running:1.0", SimpleCapability.WRITABLE_RUNNING),
            arguments("urn:ietf:params:netconf:capability:xpath:1.0", SimpleCapability.XPATH),
            arguments("urn:ietf:params:netconf:capability:yang-library:1.0", SimpleCapability.YANG_LIBRARY),
            arguments("urn:ietf:params:netconf:capability:yang-library:1.1", SimpleCapability.YANG_LIBRARY_1_1));
    }

    @Test
    void testExiCapabilityUrn() {
        final var nonParameters = new ExiCapability(null, null);
        assertEquals("urn:ietf:params:netconf:capability:exi:1.0", nonParameters.urn());

        final var someParameters = new ExiCapability(null, ExiCapability.Schemas.BUILTIN);
        assertEquals("urn:ietf:params:netconf:capability:exi:1.0?schemas=builtin", someParameters.urn());

        final var allParameters = new ExiCapability(1000000, ExiCapability.Schemas.BASE_1_1);
        assertEquals("urn:ietf:params:netconf:capability:exi:1.0?compression=1000000&schemas=base:1.1",
            allParameters.urn());
    }

    @Test
    void testYangModuleCapabilityUrn() {
        assertThrows(NullPointerException.class, () -> new YangModuleCapability(null, null, null, null, null, null));

        final var someParameters = new YangModuleCapability("http://example.com/syslog", null, null, "Some content",
            null, null);
        assertEquals("http://example.com/syslog",someParameters.urn());

        final var allParameters = new YangModuleCapability("http://example.com/module", "test-module", "2023-08-18",
            "Some content", List.of("feature"), List.of("deviation1", "deviation2"));
        assertEquals("http://example.com/module?module=test-module&revision=2023-08-18&features=feature"
            + "&deviations=deviation1,deviation2", allParameters.urn());
    }
}
