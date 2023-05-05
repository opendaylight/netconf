/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ParameterizedCapabilityTest {

    @ParameterizedTest
    @MethodSource("provideYangModuleCapabilityParams")
    void testYangModuleCapabilityUrn(String namespace, String module, String revision, List<String> features,
            List<String> deviations, String expectedUrn) {
        final var capability = new YangModuleCapability(namespace, module, revision, features, deviations);
        assertEquals(expectedUrn, capability.urn());
    }

    @ParameterizedTest
    @MethodSource("provideExiCapabilityParams")
    void testExiCapabilityUrn(Integer compression, ExiCapability.Schemas schema, String expectedUrn) {
        final var capability = new ExiCapability(compression, schema);
        assertEquals(expectedUrn, capability.urn());
    }

    @ParameterizedTest
    @MethodSource("provideExiCapabilityParams")
    void testExiCapabilityEquals(Integer compression, ExiCapability.Schemas schema) {
        assertEquals(new ExiCapability(compression, schema), new ExiCapability(compression, schema));
    }

    @ParameterizedTest
    @MethodSource("provideYangModuleCapabilityParams")
    void testYangModuleCapabilityEquals(String namespace, String module, String revision, List<String> features,
            List<String> deviations) {
        assertEquals(new YangModuleCapability(namespace, module, revision, features, deviations),
            new YangModuleCapability(namespace, module, revision, features, deviations));
    }

    static Stream<Arguments> provideExiCapabilityParams() {
        return Stream.of(
            Arguments.of(null, null, "urn:ietf:params:netconf:capability:exi:1.0"),
            Arguments.of(1000, ExiCapability.Schemas.BUILTIN,
                "urn:ietf:params:netconf:capability:exi:1.0?compression=1000&schemas=builtin"),
            Arguments.of(1000, null, "urn:ietf:params:netconf:capability:exi:1.0?compression=1000"),
            Arguments.of(null, ExiCapability.Schemas.BASE_1_1,
                "urn:ietf:params:netconf:capability:exi:1.0?schemas=base:1.1")
        );
    }

    static Stream<Arguments> provideYangModuleCapabilityParams() {
        return Stream.of(
            Arguments.of("http://example.com", null, null, null, null, "http://example.com"),
            Arguments.of("http://example.com", "module", "2023-08-21", List.of("feature1", "feature2"),
                List.of("deviation1", "deviation2"), "http://example.com?module=module&revision=2023-08-21"
                    + "&features=feature1,feature2&deviations=deviation1,deviation2"),
            Arguments.of("http://example.com", null, "2023-08-21", null, null,
                "http://example.com?revision=2023-08-21"),
            Arguments.of("http://example.com", null, "2023-08-21", List.of("feature"), List.of("deviation"),
                "http://example.com?revision=2023-08-21&features=feature&deviations=deviation"),
            Arguments.of("http://example.com", "module", null, List.of("feature"), List.of("deviation"),
                "http://example.com?module=module&features=feature&deviations=deviation"),
            Arguments.of("http://example.com", "module", "2023-08-21", null, List.of("deviation"),
                "http://example.com?module=module&revision=2023-08-21&deviations=deviation"),
            Arguments.of("http://example.com", "module", "2023-08-21", List.of("feature"), null,
                "http://example.com?module=module&revision=2023-08-21&features=feature"),
            Arguments.of("http://example.com", "module", "2023-08-21", Collections.emptyList(), List.of("deviation"),
                "http://example.com?module=module&revision=2023-08-21&deviations=deviation"),
            Arguments.of("http://example.com", "module", "2023-08-21", List.of("feature"), Collections.emptyList(),
                "http://example.com?module=module&revision=2023-08-21&features=feature")
        );
    }

}
