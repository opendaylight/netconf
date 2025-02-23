/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.capability.ExiCapability;
import org.opendaylight.netconf.api.capability.ExiSchemas;
import org.opendaylight.netconf.api.capability.SimpleCapability;
import org.opendaylight.netconf.api.capability.YangModuleCapability;
import org.opendaylight.netconf.common.CapabilityUtil;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link CapabilityUtil}.
 */
class CapabilityUtilTest {
    @ParameterizedTest(name = "parse: {0}")
    @MethodSource("capabilities")
    void testParseCapabilityUrn(final String urn, final Capability expected) {
        assertEquals(expected, CapabilityUtil.parse(urn));
    }

    @Test
    void testExtractYangModuleCapabilities() {
        final var context = YangParserTestUtils.parseYangResourceDirectory("/capability-util-test");
        final var capList = CapabilityUtil.extractYangModuleCapabilities(context);
        assertFalse(capList.isEmpty());
        final var expected = Set.of(new YangModuleCapability("http://tech/pantheon/capabilities-test",
                "capabilities-test", "2019-06-11", List.of("first-feature", "second-feature"),
                List.of("deviator-test")),
            new YangModuleCapability("http://tech/pantheon/deviator-test", "deviator-test", "2019-06-11", null, null));
        assertNotNull(capList);
        assertEquals(2, capList.size());
        assertTrue(capList.containsAll(expected));
    }

    static Stream<Arguments> capabilities() {
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
            arguments("urn:ietf:params:netconf:capability:yang-library:1.1", SimpleCapability.YANG_LIBRARY_1_1),
            arguments("urn:ietf:params:netconf:capability:exi:1.0", new ExiCapability(null, null)),
            arguments("urn:ietf:params:netconf:capability:exi:1.0?compression=1000&schemas=builtin",
                new ExiCapability(1000, ExiSchemas.BUILTIN)),
            arguments("urn:ietf:params:netconf:capability:exi:1.0?compression=1000", new ExiCapability(1000, null)),
            arguments("urn:ietf:params:netconf:capability:exi:1.0?compression=1000&schemas=null",
                new ExiCapability(1000, null)),
            arguments("urn:ietf:params:netconf:capability:exi:1.0?schemas=dynamic",
                new ExiCapability(null, ExiSchemas.DYNAMIC)),
            arguments("urn:ietf:params:netconf:capability:exi:1.0?schemas=base:1.1",
                new ExiCapability(null, ExiSchemas.BASE_1_1)),
            arguments("http://example.com", new YangModuleCapability("http://example.com", null, null, null, null)),
            arguments("http://example.com?module=module&revision=2023-08-21&features=feature1,feature2"
                + "&deviations=deviation1,deviation2",
                new YangModuleCapability("http://example.com", "module", "2023-08-21", List.of("feature1", "feature2"),
                    List.of("deviation1", "deviation2"))),
            arguments("http://example.com?revision=2023-08-21", new YangModuleCapability("http://example.com", null,
                    "2023-08-21", null, null)),
            arguments("http://example.com?revision=2023-08-21&features=feature&deviations=deviation",
                new YangModuleCapability("http://example.com", null, "2023-08-21", List.of("feature"),
                    List.of("deviation"))),
            arguments("http://example.com?module=module&features=feature&deviations=deviation",
                new YangModuleCapability("http://example.com", "module", null, List.of("feature"),
                    List.of("deviation"))),
            arguments("http://example.com?module=module&revision=2023-08-21&deviations=deviation",
                new YangModuleCapability("http://example.com", "module", "2023-08-21", null, List.of("deviation"))),
            arguments("http://example.com?module=module&revision=2023-08-21&features=feature",
                new YangModuleCapability("http://example.com", "module", "2023-08-21", List.of("feature"), null)),
            arguments("http://example.com?module=module&revision=2023-08-21&deviations=deviation",
                new YangModuleCapability("http://example.com", "module", "2023-08-21", List.of(),
                    List.of("deviation"))),
            arguments("http://example.com?module=module&revision=2023-08-21&features=feature",
                new YangModuleCapability("http://example.com", "module", "2023-08-21", List.of("feature"),
                    List.of()))
        );
    }
}
