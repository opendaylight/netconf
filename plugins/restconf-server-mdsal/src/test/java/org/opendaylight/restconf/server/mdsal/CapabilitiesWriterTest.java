/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;

class CapabilitiesWriterTest {
    @Test
    void restconfStateCapabilitiesTest() {
        final var capability = CapabilitiesWriter.mapCapabilities();
        assertEquals(CapabilitiesWriter.CAPABILITY, capability.name());

        final var entries = capability.body().stream().map(LeafSetEntryNode::body).toList();
        final var unique = Set.copyOf(entries);
        assertEquals(Set.of(
            "urn:ietf:params:restconf:capability:depth:1.0",
            "urn:ietf:params:restconf:capability:fields:1.0",
            "urn:ietf:params:restconf:capability:filter:1.0",
            "urn:ietf:params:restconf:capability:replay:1.0",
            "urn:ietf:params:restconf:capability:with-defaults:1.0",
            "urn:ietf:params:restconf:capability:yang-patch:1.0",
            "urn:opendaylight:params:restconf:capability:pretty-print:1.0",
            "urn:opendaylight:params:restconf:capability:leaf-nodes-only:1.0",
            "urn:opendaylight:params:restconf:capability:changed-leaf-nodes-only:1.0",
            "urn:opendaylight:params:restconf:capability:skip-notification-data:1.0",
            "urn:opendaylight:params:restconf:capability:child-nodes-only:1.0"), unique);
        assertEquals(unique.size(), entries.size());
    }
}
