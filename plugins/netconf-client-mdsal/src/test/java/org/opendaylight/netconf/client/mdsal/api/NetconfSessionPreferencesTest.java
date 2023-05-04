/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;

public class NetconfSessionPreferencesTest {
    @Test
    public void testMerge() {
        final var sessionCaps1 = NetconfSessionPreferences.fromStrings(List.of(
            "namespace:1?module=module1&revision=2012-12-12",
            "namespace:2?module=module2&amp;revision=2012-12-12",
            "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring"
                + "&amp;revision=2010-10-04",
            "urn:ietf:params:netconf:base:1.0",
            "urn:ietf:params:netconf:capability:rollback-on-error:1.0"));
        assertCaps(sessionCaps1, 2, 3);

        final var sessionCaps2 = NetconfSessionPreferences.fromStrings(List.of(
            "namespace:3?module=module3&revision=2012-12-12",
            "namespace:4?module=module4&revision=2012-12-12",
            "randomNonModuleCap"));
        assertCaps(sessionCaps2, 1, 2);

        final var merged = sessionCaps1.addModuleCaps(sessionCaps2);
        assertCaps(merged, 2, 2 + 1 /*Preserved monitoring*/ + 2 /*already present*/);
        for (var qname : sessionCaps2.moduleBasedCaps().keySet()) {
            assertThat(merged.moduleBasedCaps(), hasKey(qname));
        }
        assertThat(merged.moduleBasedCaps(), hasKey(NetconfMessageTransformUtil.IETF_NETCONF_MONITORING));

        assertThat(merged.nonModuleCaps(), hasKey("urn:ietf:params:netconf:base:1.0"));
        assertThat(merged.nonModuleCaps(), hasKey("urn:ietf:params:netconf:capability:rollback-on-error:1.0"));
    }

    @Test
    public void testReplace() throws Exception {
        final var sessionCaps1 = NetconfSessionPreferences.fromStrings(List.of(
            "namespace:1?module=module1&revision=2012-12-12",
            "namespace:2?module=module2&amp;revision=2012-12-12",
            "urn:ietf:params:xml:ns:yang:"
                    + "ietf-netconf-monitoring?module=ietf-netconf-monitoring&amp;revision=2010-10-04",
            "urn:ietf:params:netconf:base:1.0",
            "urn:ietf:params:netconf:capability:rollback-on-error:1.0"));
        assertCaps(sessionCaps1, 2, 3);

        final var sessionCaps2 = NetconfSessionPreferences.fromStrings(List.of(
            "namespace:3?module=module3&revision=2012-12-12",
            "namespace:4?module=module4&revision=2012-12-12",
            "randomNonModuleCap"));
        assertCaps(sessionCaps2, 1, 2);
        assertCaps(sessionCaps1.replaceModuleCaps(sessionCaps2), 2, 2);
    }

    @Test
    public void testNonModuleMerge() throws Exception {
        final var sessionCaps1 = NetconfSessionPreferences.fromStrings(List.of(
            "namespace:1?module=module1&revision=2012-12-12",
            "namespace:2?module=module2&amp;revision=2012-12-12",
            "urn:ietf:params:xml:ns:yang:"
                    + "ietf-netconf-monitoring?module=ietf-netconf-monitoring&amp;revision=2010-10-04",
            "urn:ietf:params:netconf:base:1.0",
            "urn:ietf:params:netconf:capability:rollback-on-error:1.0",
            "urn:ietf:params:netconf:capability:candidate:1.0"));
        assertCaps(sessionCaps1, 3, 3);
        assertTrue(sessionCaps1.isCandidateSupported());

        final var sessionCaps2 = NetconfSessionPreferences.fromStrings(List.of(
            "namespace:3?module=module3&revision=2012-12-12",
            "namespace:4?module=module4&revision=2012-12-12",
            "urn:ietf:params:netconf:capability:writable-running:1.0",
            "urn:ietf:params:netconf:capability:notification:1.0"));
        assertCaps(sessionCaps2, 2, 2);
        assertTrue(sessionCaps2.isRunningWritable());

        final var merged = sessionCaps1.addNonModuleCaps(sessionCaps2);

        assertCaps(merged, 3 + 2, 3);
        sessionCaps2.nonModuleCaps().forEach(
            (capability, origin) -> assertThat(merged.nonModuleCaps(), hasKey(capability)));

        assertThat(merged.nonModuleCaps(), hasKey("urn:ietf:params:netconf:base:1.0"));
        assertThat(merged.nonModuleCaps(), hasKey("urn:ietf:params:netconf:capability:rollback-on-error:1.0"));
        assertThat(merged.nonModuleCaps(), hasKey("urn:ietf:params:netconf:capability:writable-running:1.0"));
        assertThat(merged.nonModuleCaps(), hasKey("urn:ietf:params:netconf:capability:notification:1.0"));

        assertTrue(merged.isCandidateSupported());
        assertTrue(merged.isRunningWritable());
    }

    @Test
    public void testNonmoduleReplace() throws Exception {
        final var sessionCaps1 = NetconfSessionPreferences.fromStrings(List.of(
            "namespace:1?module=module1&revision=2012-12-12",
            "namespace:2?module=module2&amp;revision=2012-12-12",
            "urn:ietf:params:xml:ns:yang:"
                    + "ietf-netconf-monitoring?module=ietf-netconf-monitoring&amp;revision=2010-10-04",
            "urn:ietf:params:netconf:base:1.0",
            "urn:ietf:params:netconf:capability:rollback-on-error:1.0",
            "urn:ietf:params:netconf:capability:candidate:1.0"));
        assertCaps(sessionCaps1, 3, 3);
        assertTrue(sessionCaps1.isCandidateSupported());

        final var sessionCaps2 = NetconfSessionPreferences.fromStrings(List.of(
            "namespace:3?module=module3&revision=2012-12-12",
            "namespace:4?module=module4&revision=2012-12-12",
            "randomNonModuleCap",
            "urn:ietf:params:netconf:capability:writable-running:1.0"));
        assertCaps(sessionCaps2, 2, 2);
        assertTrue(sessionCaps2.isRunningWritable());

        final var replaced = sessionCaps1.replaceNonModuleCaps(sessionCaps2);
        assertCaps(replaced, 2, 3);
        assertFalse(replaced.isCandidateSupported());
        assertTrue(replaced.isRunningWritable());
    }

    @Test
    public void testCapabilityNoRevision() throws Exception {
        assertCaps(NetconfSessionPreferences.fromStrings(List.of(
            "namespace:2?module=module2",
            "namespace:2?module=module2&amp;revision=2012-12-12",
            "namespace:2?module=module1&amp;RANDOMSTRING;revision=2013-12-12",
            // Revision parameter present, but no revision defined
            "namespace:2?module=module4&amp;RANDOMSTRING;revision=",
            // This one should be ignored(same as first), since revision is in wrong format
            "namespace:2?module=module2&amp;RANDOMSTRING;revision=2013-12-12")),
            0, 4);
    }

    private static void assertCaps(final NetconfSessionPreferences sessionCaps1, final int nonModuleCaps,
            final int moduleCaps) {
        assertEquals(nonModuleCaps, sessionCaps1.nonModuleCaps().size());
        assertEquals(moduleCaps, sessionCaps1.moduleBasedCaps().size());
    }
}
