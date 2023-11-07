/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.Deviation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link RestconfStateStreams}.
 */
class RestconfStateStreamsTest {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfStateStreamsTest.class);

    // FIXME: assemble these from dependencies
    private static EffectiveModelContext schemaContext =
        YangParserTestUtils.parseYangResourceDirectory("/modules/restconf-module-testing");
    private static EffectiveModelContext schemaContextMonitoring =
        YangParserTestUtils.parseYangResourceDirectory("/modules");

    @Test
    void toStreamEntryNodeTest() throws Exception {
        final var path = ParserIdentifier.toInstanceIdentifier(
                "nested-module:depth1-cont/depth2-leaf1", schemaContextMonitoring, null).getInstanceIdentifier();
        final var outputType = "XML";
        final var uri = "uri";
        final var streamName = "/nested-module:depth1-cont/depth2-leaf1";

        assertMappedData(prepareMap(streamName, uri, outputType),
            ListenersBroker.streamEntry(streamName, "description", "location", outputType));
    }

    @Test
    void toStreamEntryNodeNotifiTest() throws Exception {
        final var outputType = "JSON";
        final var uri = "uri";

        assertMappedData(prepareMap("notifi", uri, outputType),
            ListenersBroker.streamEntry("notifi", "description", "location", outputType));
    }

    private static Map<QName, Object> prepareMap(final String name, final String uri, final String outputType) {
        return Map.of(
            ListenersBroker.NAME_QNAME, name,
            ListenersBroker.LOCATION_QNAME, uri,
            ListenersBroker.ENCODING_QNAME, outputType,
            ListenersBroker.DESCRIPTION_QNAME, "description");
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

    /**
     * Verify whether the loaded modules contain any deviations.
     *
     * @param containerNode
     *             modules
     */
    // FIXME: what is this supposed to verify?
    private static void verifyDeviations(final ContainerNode containerNode) {
        int deviationsFound = 0;
        for (var child : containerNode.body()) {
            if (child instanceof MapNode mapChild) {
                for (var mapEntryNode : mapChild.body()) {
                    for (var dataContainerChild : mapEntryNode.body()) {
                        if (dataContainerChild.name().getNodeType().equals(Deviation.QNAME)) {
                            deviationsFound++;
                        }
                    }
                }
            }
        }
        assertTrue(deviationsFound > 0);
    }

    /**
     * Verify loaded modules.
     *
     * @param containerNode
     *             modules
     */
    // FIXME: what is this supposed to verify?
    private static void verifyLoadedModules(final ContainerNode containerNode) {
        final var loadedModules = new HashMap<String, String>();

        for (var child : containerNode.body()) {
            if (child instanceof LeafNode) {
                assertEquals(QName.create(Module.QNAME, "module-set-id"), child.name().getNodeType());
            }
            if (child instanceof MapNode mapChild) {
                assertEquals(Module.QNAME, child.name().getNodeType());
                for (var mapEntryNode : mapChild.body()) {
                    String name = "";
                    String revision = "";
                    for (var dataContainerChild : mapEntryNode.body()) {
                        switch (dataContainerChild.name().getNodeType().getLocalName()) {
                            case "name":
                                name = String.valueOf(dataContainerChild.body());
                                break;
                            case "revision":
                                revision = String.valueOf(dataContainerChild.body());
                                break;
                            default :
                                LOG.info("Unknown local name '{}' of node.",
                                    dataContainerChild.name().getNodeType().getLocalName());
                                break;
                        }
                    }
                    loadedModules.put(name, revision);
                }
            }
        }

        final var expectedModules = schemaContext.getModules();
        assertEquals(expectedModules.size(), loadedModules.size());
        for (var m : expectedModules) {
            final String name = m.getName();
            final String revision = loadedModules.get(name);
            assertNotNull("Expected module not found", revision);
            assertEquals(Revision.ofNullable(revision), m.getRevision());

            loadedModules.remove(name);
        }
    }
}
