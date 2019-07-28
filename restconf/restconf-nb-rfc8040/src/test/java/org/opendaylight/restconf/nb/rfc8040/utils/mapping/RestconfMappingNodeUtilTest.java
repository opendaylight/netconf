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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.net.URI;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil.StreamAccessMonitoringData;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.SchemaPathCodec;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
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
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link RestconfMappingNodeUtil}.
 */
public class RestconfMappingNodeUtilTest {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfMappingNodeUtilTest.class);

    private static final QNameModule MODULE_EXAMPLE_NOTIFICATIONS = QNameModule.create(
            URI.create("urn:ietf:paras:xml:ns:yang:example-notifications"), Revision.of("2019-07-27"));

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
        // verify deviations
        verifyDeviations((ContainerNode) mods);
    }

    @Test
    public void restconfStateCapabilitesTest() {
        final Module monitoringModule = schemaContextMonitoring.findModule(MonitoringModule.MODULE_QNAME).get();
        final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode =
                RestconfMappingNodeUtil.mapCapabilites(monitoringModule);
        assertNotNull(normNode);
        final List<Object> listOfValues = new ArrayList<>();

        for (final DataContainerChild<? extends PathArgument, ?> child : normNode.getValue()) {
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
    public void mapNotificationStreamsTest() {
        // preparation of input data and expected data
        final List<List<QName>> notificationsSchemaPaths = Lists.newArrayList(
                Lists.newArrayList(QName.create(MODULE_EXAMPLE_NOTIFICATIONS, "root-notify")),
                Lists.newArrayList(
                        QName.create(MODULE_EXAMPLE_NOTIFICATIONS, "example-container"),
                        QName.create(MODULE_EXAMPLE_NOTIFICATIONS, "notification-under-container")),
                Lists.newArrayList(
                        QName.create(MODULE_EXAMPLE_NOTIFICATIONS, "example-container"),
                        QName.create(MODULE_EXAMPLE_NOTIFICATIONS, "sub-list"),
                        QName.create(MODULE_EXAMPLE_NOTIFICATIONS, "sub-notification")));
        final Map<NotificationDefinition, List<StreamAccessMonitoringData>> notificationDefinitionMap
                = generateNotificationDefinitions(notificationsSchemaPaths);
        final Set<StreamEntry> expectedStreamEntries = Sets.newHashSet(
                new StreamEntry("/example-notifications:root-notify", null, false, 2, true),
                new StreamEntry("/example-notifications:example-container/notification-under-container",
                        "Test description", false, 2, true),
                new StreamEntry("/example-notifications:example-container/sub-list/sub-notification",
                        null, false, 2, true));

        // creation of the mapping and testing of the returned container node
        final ContainerNode streamsNode = RestconfMappingNodeUtil.mapNotificationStreams(
                schemaContextMonitoring, notificationDefinitionMap);
        testStreamEntries(expectedStreamEntries, streamsNode);
    }

    @Test
    public void mapDataChangeStreamTest() {
        // preparation of input data and expected data
        final YangInstanceIdentifier sampleAccessEntryYiid = YangInstanceIdentifier.builder()
                .node(MonitoringModule.CONT_RESTCONF_STATE_QNAME)
                .node(MonitoringModule.CONT_STREAMS_QNAME)
                .node(MonitoringModule.LIST_STREAM_QNAME)
                .nodeWithKey(MonitoringModule.LIST_STREAM_QNAME, MonitoringModule.LEAF_NAME_STREAM_QNAME,
                        "/example-entry")
                .node(MonitoringModule.LEAF_REPLAY_SUPP_STREAM_QNAME)
                .build();
        final StreamAccessMonitoringData streamAccessMonitoringData = new StreamAccessMonitoringData(
                URI.create(IdentifierCodec.serialize(sampleAccessEntryYiid, schemaContextMonitoring) + "/JSON"),
                NotificationOutputType.JSON);
        final Set<StreamEntry> expectedStreamEntries = Collections.singleton(
                new StreamEntry("ietf-restconf-monitoring:restconf-state/streams/stream=%2Fexample-entry/"
                        + "replay-support", "Indicates if replay buffer is supported for this stream.\n"
                        + "If 'true', then the server MUST support the 'start-time'\n"
                        + "and 'stop-time' query parameters for this stream.", false, 1, true));

        // creation of the mapping and testing of the returned container node
        final ContainerNode streamsNode = RestconfMappingNodeUtil.mapDataChangeStream(
                schemaContextMonitoring, sampleAccessEntryYiid, streamAccessMonitoringData);
        testStreamEntries(expectedStreamEntries, streamsNode);
    }

    @Test
    public void mapStreamSubscriptionTest() {
        // preparation of input data and expected data
        final String sampleStreamName = "ietf-restconf-monitoring:restconf-state/streams/stream=example-stream";
        final Set<StreamEntry> expectedStreamEntries = Collections.singleton(
                new StreamEntry("ietf-restconf-monitoring:restconf-state/streams/stream=example-stream", null,
                        true, 0, false));

        // creation of the mapping and testing of the returned container node
        final MapEntryNode streamEntry = RestconfMappingNodeUtil.mapStreamSubscription(sampleStreamName, Instant.now());
        testStreamEntry(expectedStreamEntries, streamEntry);
    }

    private static void testStreamEntries(final Set<StreamEntry> expectedStreamEntries,
                                          final ContainerNode streamsNode) {
        Assert.assertEquals(1, streamsNode.getValue().size());
        final MapNode streamsList = (MapNode) streamsNode.getValue().iterator().next();
        Assert.assertEquals(MonitoringModule.LIST_STREAM_QNAME, streamsList.getNodeType());
        final Collection<MapEntryNode> streamEntries = streamsList.getValue();
        Assert.assertEquals(expectedStreamEntries.size(), streamEntries.size());
        streamEntries.forEach(mapEntryNode -> testStreamEntry(expectedStreamEntries, mapEntryNode));
    }

    private static void testStreamEntry(final Set<StreamEntry> expectedStreamEntries, final MapEntryNode mapEntryNode) {
        final boolean replaySupport = mapEntryNode.getChild(
                NodeIdentifier.create(MonitoringModule.LEAF_REPLAY_SUPP_STREAM_QNAME))
                .map(dataContainerChild -> Boolean.valueOf(dataContainerChild.getValue().toString()))
                .orElse(false);
        final String streamName = mapEntryNode.getChild(NodeIdentifier.create(
                MonitoringModule.LEAF_NAME_STREAM_QNAME))
                .map(dataContainerChild -> dataContainerChild.getValue().toString())
                .orElse(null);
        final String description = mapEntryNode.getChild(NodeIdentifier.create(
                MonitoringModule.LEAF_DESCR_STREAM_QNAME))
                .map(dataContainerChild -> dataContainerChild.getValue().toString())
                .orElse(null);
        final boolean setReplayLogCreationTime = mapEntryNode.getChild(NodeIdentifier.create(
                MonitoringModule.LEAF_START_TIME_STREAM_QNAME)).isPresent();
        final int accessEntries = mapEntryNode.getChild(
                NodeIdentifier.create(MonitoringModule.LIST_ACCESS_STREAM_QNAME))
                .map(dataContainerChild -> ((MapNode) dataContainerChild).getValue().size())
                .orElse(0);
        Assert.assertTrue(expectedStreamEntries.contains(new StreamEntry(streamName, description,
                setReplayLogCreationTime, accessEntries, replaySupport)));
    }

    private static Map<NotificationDefinition, List<StreamAccessMonitoringData>> generateNotificationDefinitions(
            final List<List<QName>> notificationsSchemaPaths) {
        return notificationsSchemaPaths.stream()
                .map(schemaPath -> (NotificationDefinition) SchemaContextUtil.findNodeInSchemaContext(
                        schemaContextMonitoring, schemaPath))
                .map(notificationDefinition -> {
                    // in this test, for simplification, serialized path is used directly to create target URI
                    final String serializedPath = SchemaPathCodec.serialize(
                            notificationDefinition.getPath(), schemaContextMonitoring);
                    final List<StreamAccessMonitoringData> accessData = Lists.newArrayList(
                            new StreamAccessMonitoringData(URI.create(serializedPath + '/'
                                    + NotificationOutputType.JSON.name()), NotificationOutputType.JSON),
                            new StreamAccessMonitoringData(URI.create(serializedPath + '/'
                                    + NotificationOutputType.XML.name()), NotificationOutputType.XML));
                    return new AbstractMap.SimpleEntry<>(notificationDefinition, accessData);
                })
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
    }

    /**
     * Verify whether the loaded modules contain any deviations.
     *
     * @param containerNode
     *             modules
     */
    private static void verifyDeviations(final ContainerNode containerNode) {
        int deviationsFound = 0;
        for (final DataContainerChild child : containerNode.getValue()) {
            if (child instanceof MapNode) {
                for (final MapEntryNode mapEntryNode : ((MapNode) child).getValue()) {
                    for (final DataContainerChild dataContainerChild : mapEntryNode
                            .getValue()) {
                        if (dataContainerChild.getNodeType()
                                .equals(IetfYangLibrary.SPECIFIC_MODULE_DEVIATION_LIST_QNAME)) {
                            deviationsFound++;
                        }
                    }
                }
            }
        }
        Assert.assertTrue(deviationsFound > 0);
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
                assertEquals(IetfYangLibrary.MODULE_SET_ID_LEAF_QNAME, child.getNodeType());
            }
            if (child instanceof MapNode) {
                assertEquals(IetfYangLibrary.MODULE_QNAME_LIST, child.getNodeType());
                for (final MapEntryNode mapEntryNode : ((MapNode) child).getValue()) {
                    String name = "";
                    String revision = "";
                    for (final DataContainerChild<? extends PathArgument, ?> dataContainerChild : mapEntryNode
                            .getValue()) {
                        switch (dataContainerChild.getNodeType().getLocalName()) {
                            case IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF:
                                name = String.valueOf(dataContainerChild.getValue());
                                break;
                            case IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF:
                                revision = String.valueOf(dataContainerChild.getValue());
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

    private static final class StreamEntry {
        private final String streamName;
        private final String description;
        private final boolean setReplayLogCreationTime;
        private final int accessEntries;
        private final boolean replaySupport;

        private StreamEntry(final String streamName, final String description, final boolean setReplayLogCreationTime,
                final int accessEntries, final boolean replaySupport) {
            this.streamName = streamName;
            this.description = description;
            this.setReplayLogCreationTime = setReplayLogCreationTime;
            this.accessEntries = accessEntries;
            this.replaySupport = replaySupport;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            StreamEntry that = (StreamEntry) object;
            return accessEntries == that.accessEntries && Objects.equals(replaySupport, that.replaySupport)
                    && Objects.equals(streamName, that.streamName)
                    && Objects.equals(description, that.description)
                    && Objects.equals(setReplayLogCreationTime, that.setReplayLogCreationTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(streamName, description, setReplayLogCreationTime, accessEntries, replaySupport);
        }
    }
}