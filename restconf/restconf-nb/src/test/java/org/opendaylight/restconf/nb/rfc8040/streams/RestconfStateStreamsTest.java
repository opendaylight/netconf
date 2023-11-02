/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.Deviation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
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
public class RestconfStateStreamsTest {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfStateStreamsTest.class);

    private static EffectiveModelContext schemaContext;
    private static EffectiveModelContext schemaContextMonitoring;

    @BeforeClass
    public static void loadTestSchemaContextAndModules() throws Exception {
        // FIXME: assemble these from dependencies
        schemaContext = YangParserTestUtils.parseYangResourceDirectory("/modules/restconf-module-testing");
        schemaContextMonitoring = YangParserTestUtils.parseYangResourceDirectory("/modules");
    }

    @Test
    public void toStreamEntryNodeTest() throws Exception {
        final YangInstanceIdentifier path = ParserIdentifier.toInstanceIdentifier(
                "nested-module:depth1-cont/depth2-leaf1", schemaContextMonitoring, null).getInstanceIdentifier();
        final String outputType = "XML";
        final URI uri = new URI("uri");
        final String streamName = "/nested-module:depth1-cont/depth2-leaf1";

        assertMappedData(prepareMap(streamName, uri, outputType),
            RestconfStateStreams.dataChangeStreamEntry(path, outputType, uri, schemaContextMonitoring, streamName));
    }

    @Test
    public void toStreamEntryNodeNotifiTest() throws Exception {
        final String outputType = "JSON";
        final URI uri = new URI("uri");

        final var map = prepareMap("notifi", uri, outputType);
        map.put(RestconfStateStreams.DESCRIPTION_QNAME, "(urn:nested:module?revision=2014-06-03)notifi");

        assertMappedData(map, RestconfStateStreams.notificationStreamEntry("notifi",
            Set.of(QName.create("urn:nested:module", "2014-06-03", "notifi")), outputType, uri));
    }

    private static Map<QName, Object> prepareMap(final String name, final URI uri, final String outputType) {
        final var map = new HashMap<QName, Object>();
        map.put(RestconfStateStreams.NAME_QNAME, name);
        map.put(RestconfStateStreams.LOCATION_QNAME, uri.toString());
        map.put(RestconfStateStreams.ENCODING_QNAME, outputType);
        return map;
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
    private static void verifyLoadedModules(final ContainerNode containerNode) {
        final Map<String, String> loadedModules = new HashMap<>();

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
        assertEquals("Number of loaded modules is not as expected", expectedModules.size(), loadedModules.size());
        for (var m : expectedModules) {
            final String name = m.getName();
            final String revision = loadedModules.get(name);
            assertNotNull("Expected module not found", revision);
            assertEquals("Incorrect revision of loaded module", Revision.ofNullable(revision), m.getRevision());

            loadedModules.remove(name);
        }
    }
}
