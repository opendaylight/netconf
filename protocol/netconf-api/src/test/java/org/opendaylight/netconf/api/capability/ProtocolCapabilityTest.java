/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
}
