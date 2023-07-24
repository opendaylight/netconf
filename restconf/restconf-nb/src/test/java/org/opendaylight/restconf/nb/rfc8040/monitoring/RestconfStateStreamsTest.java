/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.monitoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.nb.rfc8040.legacy.SchemaContextHandler;
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

    /**
     * Test of writing modules into {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} and checking if modules were
     * correctly written.
     */
    @Test
    public void restconfMappingNodeTest() {
        // write modules into list module in Restconf
        final ContainerNode mods = SchemaContextHandler.mapModulesByIetfYangLibraryYang(schemaContext, "1");

        // verify loaded modules
        verifyLoadedModules(mods);
        // verify deviations
        verifyDeviations(mods);
    }

    @Test
    public void toStreamEntryNodeTest() throws Exception {
        final YangInstanceIdentifier path = ParserIdentifier.toInstanceIdentifier(
                "nested-module:depth1-cont/depth2-leaf1", schemaContextMonitoring, null).getInstanceIdentifier();
        final Instant start = Instant.now();
        final String outputType = "XML";
        final URI uri = new URI("uri");
        final String streamName = "/nested-module:depth1-cont/depth2-leaf1";

        final Map<QName, Object> map = prepareMap(streamName, uri, start, outputType);
        final MapEntryNode mappedData = RestconfStateStreams.dataChangeStreamEntry(path, start, outputType, uri,
            schemaContextMonitoring, streamName);
        assertMappedData(map, mappedData);
    }

    @Test
    public void toStreamEntryNodeNotifiTest() throws Exception {
        final Instant start = Instant.now();
        final String outputType = "JSON";
        final URI uri = new URI("uri");

        final Map<QName, Object> map = prepareMap("notifi", uri, start, outputType);
        map.put(RestconfStateStreams.DESCRIPTION_QNAME, "Notifi");

        final MapEntryNode mappedData = RestconfStateStreams.notificationStreamEntry(schemaContextMonitoring,
            QName.create("urn:nested:module", "2014-06-03", "notifi"), start, outputType, uri);
        assertMappedData(map, mappedData);
    }

    private static Map<QName, Object> prepareMap(final String name, final URI uri, final Instant start,
            final String outputType) {
        final Map<QName, Object> map = new HashMap<>();
        map.put(RestconfStateStreams.NAME_QNAME, name);
        map.put(RestconfStateStreams.LOCATION_QNAME, uri.toString());
        map.put(RestconfStateStreams.REPLAY_SUPPORT_QNAME, Boolean.TRUE);
        map.put(RestconfStateStreams.REPLAY_LOG_CREATION_TIME, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            OffsetDateTime.ofInstant(start, ZoneId.systemDefault())));
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
