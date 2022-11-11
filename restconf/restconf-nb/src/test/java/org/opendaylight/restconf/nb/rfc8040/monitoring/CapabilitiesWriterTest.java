/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.monitoring;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;

import java.util.stream.Collectors;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Capabilities;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;

public class CapabilitiesWriterTest {
    @Test
    public void restconfStateCapabilitesTest() {
        final var normNode = CapabilitiesWriter.mapCapabilites();
        assertEquals(Capabilities.QNAME, normNode.getIdentifier().getNodeType());

        @SuppressWarnings("unchecked")
        final var capability = (LeafSetNode<String>) normNode.body().stream()
            // Find 'capability' leaf-list
            .filter(child -> CapabilitiesWriter.CAPABILITY.equals(child.getIdentifier()))
            .findFirst()
            .orElseThrow();

        assertThat(
            capability.body().stream().map(entry -> ((LeafSetEntryNode<?>) entry).body()).collect(Collectors.toList()),
            containsInAnyOrder(
                equalTo("urn:ietf:params:restconf:capability:depth:1.0"),
                equalTo("urn:ietf:params:restconf:capability:fields:1.0"),
                equalTo("urn:ietf:params:restconf:capability:filter:1.0"),
                equalTo("urn:ietf:params:restconf:capability:replay:1.0"),
                equalTo("urn:ietf:params:restconf:capability:with-defaults:1.0"),
                equalTo("urn:opendaylight:params:restconf:capability:pretty-print:1.0"),
                equalTo("urn:opendaylight:params:restconf:capability:leaf-nodes-only:1.0"),
                equalTo("urn:opendaylight:params:restconf:capability:skip-notification-data:1.0")));
    }
}
