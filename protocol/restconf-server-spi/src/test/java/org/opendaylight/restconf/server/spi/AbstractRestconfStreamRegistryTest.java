/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link AbstractRestconfStreamRegistry}.
 */
class AbstractRestconfStreamRegistryTest {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractRestconfStreamRegistryTest.class);
    private static final EffectiveModelContext CONTEXT =
        // TODO: assemble these from dependencies?
        YangParserTestUtils.parseYangResourceDirectory("/modules/restconf-module-testing");

    @Test
    void toStreamEntryNodeTest() throws Exception {
        final var outputType = "XML";
        final var uri = "uri";
        final var streamName = "/nested-module:depth1-cont/depth2-leaf1";

        assertMappedData(prepareMap(streamName, uri, outputType),
            AbstractRestconfStreamRegistry.streamEntry(streamName, "description", "location",
                Set.of(new EncodingName(outputType))));
    }

    @Test
    void toStreamEntryNodeNotifiTest() throws Exception {
        final var outputType = "JSON";
        final var uri = "uri";

        assertMappedData(prepareMap("notifi", uri, outputType),
            AbstractRestconfStreamRegistry.streamEntry("notifi", "description", "location",
                Set.of(new EncodingName(outputType))));
    }

    private static Map<QName, Object> prepareMap(final String name, final String uri, final String outputType) {
        return Map.of(
            AbstractRestconfStreamRegistry.NAME_QNAME, name,
            AbstractRestconfStreamRegistry.LOCATION_QNAME, uri,
            AbstractRestconfStreamRegistry.ENCODING_QNAME, outputType,
            AbstractRestconfStreamRegistry.DESCRIPTION_QNAME, "description");
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
