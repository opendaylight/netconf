/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

public class CapabilityUtilTest {
    // TODO consider making it into ParameterizedTest
    @Test
    public void testParseSimpleCapUrn() {
        final var simpleCapUrn = "urn:ietf:params:netconf:base:1.0";

        final var simpleCap = CapabilityUtil.parse(simpleCapUrn);
        assertNotNull(simpleCap);
        assertSame(SimpleCapability.BASE, simpleCap);
    }

    @Test
    public void testParseExiCapUrn() {
        final var exiCapUrn = "urn:ietf:params:netconf:capability:exi:1.0?compression=1000&schemas=builtin";
        final var exiCapUrnNoParam = "urn:ietf:params:netconf:capability:exi:1.0";

        final var exiCap = CapabilityUtil.parse(exiCapUrn);
        assertNotNull(exiCap);
        assertTrue(exiCap instanceof ExiCapability);

        // FIXME check for ExiCapability after previous patch will be rebased
        final var exiCapNoParam = CapabilityUtil.parse(exiCapUrnNoParam);
        assertNotNull(exiCapNoParam);
        assertTrue(exiCapNoParam instanceof SimpleCapability);
    }

    @Test
    public void testParseYangModuleCapUrn() {
        final var yangModuleCapUrn = "http://example.com?revision=2023-08-21&features=feature&deviations=deviation";
        final var yangModuleCapUrnNoParam = "http://example.com";

        final var yangModuleCap = CapabilityUtil.parse(yangModuleCapUrn);
        assertNotNull(yangModuleCap);
        assertTrue(yangModuleCap instanceof YangModuleCapability);

        final var yangModuleCapNoParam = CapabilityUtil.parse(yangModuleCapUrnNoParam);
        assertNotNull(yangModuleCapNoParam);
        assertTrue(yangModuleCapNoParam instanceof YangModuleCapability);
    }
}
