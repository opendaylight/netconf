/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;

/**
 * Unit tests for {@link Rfc8040StreamSupport}.
 */
class Rfc8040StreamSupportTest {
    @Test
    void toStreamEntryNodeTest() throws Exception {
        final var streamName = "/nested-module:depth1-cont/depth2-leaf1";

        assertMappedData(prepareMap(streamName, "uri", "xml"),
            Rfc8040StreamSupport.streamEntry(streamName, "description", "location", Set.of(EncodeXml$I.QNAME)));
    }

    @Test
    void toStreamEntryNodeNotifiTest() throws Exception {
        assertMappedData(prepareMap("notifi", "uri", "json"),
            Rfc8040StreamSupport.streamEntry("notifi", "description", "location", Set.of(EncodeJson$I.QNAME)));
    }

    private static Map<QName, Object> prepareMap(final String name, final String uri, final String outputType) {
        return Map.of(
            Rfc8040StreamSupport.NAME_QNAME, name,
            Rfc8040StreamSupport.LOCATION_QNAME, uri,
            Rfc8040StreamSupport.ENCODING_QNAME, outputType,
            Rfc8040StreamSupport.DESCRIPTION_QNAME, "description");
    }

    private static void assertMappedData(final Map<QName, Object> map, final MapEntryNode mappedData) {
        assertNotNull(mappedData);
        for (var child : mappedData.body()) {
            if (child instanceof LeafNode<?> leaf) {
                assertTrue(map.containsKey(leaf.name().getNodeType()));
                assertEquals(map.get(leaf.name().getNodeType()), leaf.body());
            }
        }
    }
}
