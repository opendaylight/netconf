/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link RestconfMappingNodeUtil}.
 */
public class RestconfMappingNodeUtilTest {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfMappingNodeUtilTest.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock private ListSchemaNode mockStreamList;
    @Mock private LeafSchemaNode leafName;
    @Mock private LeafSchemaNode leafDescription;
    @Mock private LeafSchemaNode leafReplaySupport;
    @Mock private LeafSchemaNode leafReplayLog;
    @Mock private LeafSchemaNode leafEvents;

    private static Set<Module> modules;
    private static SchemaContext schemaContext;
    private static SchemaContext schemaContextMonitoring;

    private static Set<Module> modulesRest;

    @BeforeClass
    public static void loadTestSchemaContextAndModules() throws Exception {
        schemaContext =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/modules/restconf-module-testing"));
        schemaContextMonitoring = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/modules"));
        modules = schemaContextMonitoring.getModules();
        modulesRest = YangParserTestUtils
                .parseYangFiles(TestRestconfUtils.loadFiles("/modules/restconf-module-testing")).getModules();
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(this.leafName.getQName()).thenReturn(QName.create("", RestconfMappingNodeConstants.NAME));
        when(this.leafDescription.getQName()).thenReturn(QName.create("", RestconfMappingNodeConstants.DESCRIPTION));
        when(this.leafReplaySupport.getQName()).thenReturn(
                QName.create("", RestconfMappingNodeConstants.REPLAY_SUPPORT));
        when(this.leafReplayLog.getQName()).thenReturn(QName.create("", RestconfMappingNodeConstants.REPLAY_LOG));
        when(this.leafEvents.getQName()).thenReturn(QName.create("", RestconfMappingNodeConstants.EVENTS));
    }

    /**
     * Test of writing modules into {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} and checking if modules were
     * correctly written.
     */
    @Test
    public void restconfMappingNodeTest() {
        // write modules into list module in Restconf
        final Module ietfYangLibMod = schemaContext.findModule(IetfYangLibrary.MODULE_QNAME).get();
        final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> mods =
                RestconfMappingNodeUtil.mapModulesByIetfYangLibraryYang(RestconfMappingNodeUtilTest.modules,
                        ietfYangLibMod, schemaContext, "1");

        // verify loaded modules
        verifyLoadedModules((ContainerNode) mods);
    }

    @Test
    public void restconfStateCapabilitesTest() {
        final Module monitoringModule = schemaContextMonitoring.findModule(MonitoringModule.MODULE_QNAME).get();
        final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode =
                RestconfMappingNodeUtil.mapCapabilites(monitoringModule);
        assertNotNull(normNode);
        final List<Object> listOfValues = new ArrayList<>();

        for (final DataContainerChild<? extends PathArgument, ?> child : ((ContainerNode) normNode).getValue()) {
            if (child.getNodeType().equals(MonitoringModule.CONT_CAPABILITES_QNAME)) {
                for (final DataContainerChild<? extends PathArgument, ?> dataContainerChild : ((ContainerNode) child)
                        .getValue()) {
                    for (final Object entry : ((LeafSetNode<?>) dataContainerChild).getValue()) {
                        listOfValues.add(((LeafSetEntryNode<?>) entry).getValue());
                    }
                }
            }
        }
        Assert.assertTrue(listOfValues.contains(QueryParams.DEPTH));
        Assert.assertTrue(listOfValues.contains(QueryParams.FIELDS));
        Assert.assertTrue(listOfValues.contains(QueryParams.FILTER));
        Assert.assertTrue(listOfValues.contains(QueryParams.REPLAY));
        Assert.assertTrue(listOfValues.contains(QueryParams.WITH_DEFAULTS));
    }

    @Test
    public void toStreamEntryNodeTest() throws Exception {
        final YangInstanceIdentifier path = ParserIdentifier.toInstanceIdentifier(
                "nested-module:depth1-cont/depth2-leaf1", schemaContextMonitoring, null).getInstanceIdentifier();
        final Instant start = Instant.now();
        final String outputType = "XML";
        final URI uri = new URI("uri");
        final Module monitoringModule = schemaContextMonitoring.findModule(MonitoringModule.MODULE_QNAME).orElse(null);
        final boolean exist = true;

        final Map<QName, Object> map =
                prepareMap(path.getLastPathArgument().getNodeType().getLocalName(), uri, start, outputType);

        final NormalizedNode<?, ?> mappedData =
                RestconfMappingNodeUtil.mapDataChangeNotificationStreamByIetfRestconfMonitoring(
                        path, start, outputType, uri, monitoringModule, exist, schemaContextMonitoring);
        assertNotNull(mappedData);
        testData(map, mappedData);
    }

    @Test
    public void toStreamEntryNodeNotifiTest() throws Exception {
        final Instant start = Instant.now();
        final String outputType = "JSON";
        final URI uri = new URI("uri");
        final Module monitoringModule = schemaContextMonitoring.findModule(MonitoringModule.MODULE_QNAME).orElse(null);
        final boolean exist = true;

        final Map<QName, Object> map = prepareMap("notifi", uri, start, outputType);
        map.put(MonitoringModule.LEAF_DESCR_STREAM_QNAME, "Notifi");

        final QName notifiQName = QName.create("urn:nested:module", "2014-06-03", "notifi");
        final NormalizedNode<?, ?> mappedData =
                RestconfMappingNodeUtil.mapYangNotificationStreamByIetfRestconfMonitoring(notifiQName,
                    schemaContextMonitoring.getNotifications(), start, outputType, uri, monitoringModule, exist);
        assertNotNull(mappedData);
        testData(map, mappedData);
    }

    private static Map<QName, Object> prepareMap(final String name, final URI uri, final Instant start,
            final String outputType) {
        final Map<QName, Object> map = new HashMap<>();
        map.put(MonitoringModule.LEAF_NAME_STREAM_QNAME, name);
        map.put(MonitoringModule.LEAF_LOCATION_ACCESS_QNAME, uri.toString());
        map.put(MonitoringModule.LEAF_REPLAY_SUPP_STREAM_QNAME, Boolean.TRUE);
        map.put(MonitoringModule.LEAF_START_TIME_STREAM_QNAME, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            OffsetDateTime.ofInstant(start, ZoneId.systemDefault())));
        map.put(MonitoringModule.LEAF_ENCODING_ACCESS_QNAME, outputType);
        return map;
    }

    private static void testData(final Map<QName, Object> map, final NormalizedNode<?, ?> mappedData) {
        for (final DataContainerChild<? extends PathArgument, ?> child : ((MapEntryNode) mappedData).getValue()) {
            if (child instanceof LeafNode) {
                final LeafNode<?> leaf = (LeafNode<?>) child;
                Assert.assertTrue(map.containsKey(leaf.getNodeType()));
                Assert.assertEquals(map.get(leaf.getNodeType()), leaf.getValue());
            }
        }
    }

    /**
     * Verify loaded modules.
     *
     * @param containerNode
     *             modules
     */
    private static void verifyLoadedModules(final ContainerNode containerNode) {

        final Map<String, String> loadedModules = new HashMap<>();

        for (final DataContainerChild<? extends PathArgument, ?> child : containerNode.getValue()) {
            if (child instanceof LeafNode) {
                assertEquals(IetfYangLibrary.MODULE_SET_ID_LEAF_QNAME, ((LeafNode<?>) child).getNodeType());
            }
            if (child instanceof MapNode) {
                assertEquals(IetfYangLibrary.MODULE_QNAME_LIST, ((MapNode) child).getNodeType());
                for (final MapEntryNode mapEntryNode : ((MapNode) child).getValue()) {
                    String name = "";
                    String revision = "";
                    for (final DataContainerChild<? extends PathArgument, ?> dataContainerChild : mapEntryNode
                            .getValue()) {
                        switch (dataContainerChild.getNodeType().getLocalName()) {
                            case IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF:
                                name = String.valueOf(((LeafNode<?>) dataContainerChild).getValue());
                                break;
                            case IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF:
                                revision = String.valueOf(((LeafNode<?>) dataContainerChild).getValue());
                                break;
                            default :
                                LOG.info("Unknown local name '{}' of node.",
                                        dataContainerChild.getNodeType().getLocalName());
                                break;
                        }
                    }
                    loadedModules.put(name, revision);
                }
            }
        }

        verifyLoadedModules(RestconfMappingNodeUtilTest.modulesRest, loadedModules);
    }

    /**
     * Verify if correct modules were loaded into Restconf module by comparison with modules from
     * <code>SchemaContext</code>.
     * @param expectedModules Modules from <code>SchemaContext</code>
     * @param loadedModules Loaded modules into Restconf module
     */
    private static void verifyLoadedModules(final Set<Module> expectedModules,
            final Map<String, String> loadedModules) {
        assertEquals("Number of loaded modules is not as expected", expectedModules.size(), loadedModules.size());
        for (final Module m : expectedModules) {
            final String name = m.getName();

            final String revision = loadedModules.get(name);
            assertNotNull("Expected module not found", revision);
            assertEquals("Incorrect revision of loaded module", Revision.ofNullable(revision), m.getRevision());

            loadedModules.remove(name);
        }
    }
}
