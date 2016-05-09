/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link RestconfMappingNodeUtil}
 */
public class RestconfMappingNodeUtilTest {
    private final List<String> expectedStreams = Arrays.asList(new String[] {"stream-1", "stream-2", "stream-3"});

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private SchemaContext schemaContext;
    private Module restconfModule;
    private DataSchemaNode restconfSchemaNode;

    @Before
    public void setup() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        restconfModule = schemaContext.findModuleByName("ietf-restconf",
                SimpleDateFormatUtil.getRevisionFormat().parse("2013-10-19"));
        restconfSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                Draft11.MonitoringModule.STREAM_LIST_SCHEMA_NODE);

        // create streams
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(0));
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(1));
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(2));
    }

    @Test
    public void restconfMappingNodeTest() {
        MapNode mapNode = RestconfMappingNodeUtil.restconfMappingNode(restconfModule, schemaContext.getModules());
        assertNotNull("Mapping of modules should be successful", mapNode);

        // verify loaded modules
        Map<String, String> expectedModules = new HashMap<>();
        Iterator<MapEntryNode> iterator = mapNode.getValue().iterator();
        while (iterator.hasNext()) {
            final Iterator entries = ((AbstractImmutableDataContainerAttrNode) iterator.next())
                    .getChildren().entrySet().iterator();

            String name = null;
            String revision = null;

            while (entries.hasNext()) {
                Map.Entry e = ((AbstractMap.SimpleImmutableEntry) entries.next());
                String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                switch (key) {
                    case "name":
                        name = (String) ((LeafNode) e.getValue()).getValue();
                        break;
                    case "revision":
                        revision = (String) ((LeafNode) e.getValue()).getValue();
                        break;
                    case "namespace":
                    case "feature":
                        break;
                }
            }
            expectedModules.put(name, revision);
        }

        verifyModules(schemaContext.getModules(), expectedModules);
    }

    @Test
    public void toStreamEntryNodeTest() throws Exception {
        MapEntryNode mapEntryNode = RestconfMappingNodeUtil.toStreamEntryNode("module1", restconfSchemaNode);
        assertNotNull("Map entry node should be created", mapEntryNode);

        // verify loaded streams
        final List<String> loadedStreams = new ArrayList<>();
        final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) mapEntryNode)
                .getChildren().entrySet().iterator();

        while (mapEntries.hasNext()) {
            final Map.Entry e = ((AbstractMap.SimpleImmutableEntry) mapEntries.next());
            final String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

            switch (key) {
                case RestconfMappingNodeConstants.NAME :
                    loadedStreams.add((String) ((LeafNode) e.getValue()).getValue());
                    break;
                case RestconfMappingNodeConstants.DESCRIPTION :
                    assertEquals("Stream description value is not as expected",
                            RestconfMappingStreamConstants.DESCRIPTION, ((LeafNode) e.getValue()).getValue());
                    break;
                case RestconfMappingNodeConstants.REPLAY_SUPPORT :
                    assertEquals("Stream replay support value is not as expected",
                            RestconfMappingStreamConstants.REPLAY_SUPPORT, ((LeafNode) e.getValue()).getValue());
                    break;
                case RestconfMappingNodeConstants.REPLAY_LOG :
                    assertEquals("Stream replay log value is not as expected",
                            RestconfMappingStreamConstants.REPLAY_LOG, ((LeafNode) e.getValue()).getValue());
                    break;
                case RestconfMappingNodeConstants.EVENTS :
                    assertEquals("Stream events value is not as expected",
                            RestconfMappingStreamConstants.EVENTS, ((LeafNode) e.getValue()).getValue());
                    break;
                default:
                    throw new Exception("Unknown key in Restconf stream definition");
            }
        }

        //verifyStreams(loadedStreams);
    }

    // ** new test plan

    /**
     * Test mapping modules to list without Restconf module. Test fails with <code>NullPointerException</code>
     */
    @Test
    public void restconfMappingNodeMissingRestconfModuleTest() {
        thrown.expect(NullPointerException.class);
        RestconfMappingNodeUtil.restconfMappingNode(null, schemaContext.getModules());
    }

    /**
     * Try to map modules into module list when Restconf module is available but does not contain node with name
     * 'module'. <code>NullPointerException</code> should be returned.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void restconfMappingNodeMissingModuleListTest() {}

    /**
     * Try to map modules into module list when Restconf module is available but contains node with name
     * 'module' but it is not of type list. <code>IllegalStateException</code> should be returned.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void restconfMappingNodeModuleListTest() {}

    /**
     * Map null set of modules to module list. <code>NullPointerException</code> is expected.
     */
    @Test
    public void restconfMappingNodeNullModulesTest() {
        thrown.expect(NullPointerException.class);
        RestconfMappingNodeUtil.restconfMappingNode(restconfModule, null);
    }

    /**
     * Try to map modules into list module of Restconf module when Restconf module does not contain grouping with
     * name 'restconf'. Catching <code>RestconfDocumentedException</code> and checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void restconfMappingNodeNoRestconfGroupingTest() {}

    /**
     * Try to map modules into list module of Restconf module when Restconf module does not contain any grouping.
     * Test is catching <code>RestconfDocumentedException</code> and checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void restconfMappingNodeNoGroupingsTest() {}

    /**
     * Test when there is a container with name 'restconf' in Restconf module but contains no child nodes.
     */
    @Ignore
    @Test(expected = NoSuchElementException.class)
    public void restconfMappingNodeRestconfGroupingNoChildTest() {}

    /**FIXME
     * Test when container restconf is not the first child in grouping restconf.
     */

    /**
     * Module list in Restconf module does not contain any child nodes. Test fails with
     * <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void restconfMappingNodeModuleListNoChilds() {}

    /**
     * Module list in Restconf module does not contain any child with name 'name'. Test fails with
     * <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void restconfMappingNodeMissingNameTest() {}

    /**
     * Module list in Restconf module does not contain any child with name 'revision'. Test fails with
     * <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void restconfMappingNodeMissingRevisonTest() {}

    /**
     * Module list in Restconf module does not contain any child with name 'namespace'. Test fails with
     * <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void restconfMappingNodeMissingNamepsaceTest() {}

    /**
     * Module list in Restconf module does not contain any child with name 'features'. Test fails with
     * <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void restconfMappingNodeMissingFeaturesTest() {}

    /**
     * Module list in Restconf module contains child with name 'name' but it is not of type leaf. Test fails with
     * <code>IllegalStateException</code>.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void restconfMappingNodeNameTest() {}

    /**
     * Module list in Restconf module contains child with name 'revision' but it is not of type leaf. Test fails with
     * <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void restconfMappingNodeRevisonTest() {}

    /**
     * Module list in Restconf module contains child with name 'namespace' but it is not of type leaf. Test fails with
     * <code>IllegalStateException</code>.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void restconfMappingNodeNamepsaceTest() {}

    /**
     * Module list in Restconf module contains child with name 'features' but it is not of type leaf-list. Test fails
     * with <code>IllegalStateException</code>.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void restconfMappingNodeFeaturesTest() {}

    /**
     * Try to map streams when target <code>DataSchemaNode</code> is null. Test is expected to fail catching
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void toStreamEntryNodeNullSchemaNodeTest() {}

    /**
     * Test trying to map streams to <code>DataSchemaNode</code> which is not of type list. Test is expected to fail
     * catching <code>IllegalStateException</code>.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void toStreamEntryNodeWrongTypeOfSchemaNodeTest() {}

    /**
     * Test case with target <code>DataSchemaNode</code> which does not contain any child nodes. Test is catching
     * <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void toStreamEntryNodeSchemaNodeWithoutChilds() {}

    /**
     * Test case when target list stream does not contain child with name 'name'. Test is catching
     * <code>RestconfDocumentedException</code> and checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void toStreamEntryNodeMissingStreamNameTest() {}

    /**
     * Test case when target list stream does not contain child with name 'description'. Test is catching
     * <code>RestconfDocumentedException</code> and checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void toStreamEntryNodeMissingStreamDescriptionTest() {}

    /**
     * Test case when target list stream does not contain child with name 'replay-support'. Test is catching
     * <code>RestconfDocumentedException</code> and checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void toStreamEntryNodeMissingStreamReplaySupportTest() {}

    /**
     * Test case when target list stream does not contain child with name 'replay-log-creation-time'. Test is catching
     * <code>RestconfDocumentedException</code> and checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void toStreamEntryNodeMissingStreamReplayLogTest() {}

    /**
     * Test case when target list stream does not contain child with name 'events'. Test is catching
     * <code>RestconfDocumentedException</code> and checking error type and error tag.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void toStreamEntryNodeMissingStreamEventsTest() {}

    /**
     * Test case when target list stream contains child with name 'name'. Test is catching
     * <code>IllegalStateException</code>.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void toStreamEntryNodeStreamNameTest() {}

    /**
     * Test case when target list stream contains child with name 'description'. Test is catching
     * <code>IllegalStateException</code>.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void toStreamEntryNodeStreamDescriptionTest() {}

    /**
     * Test case when target list stream contains child with name 'replay-support'. Test is catching
     * <code>IllegalStateException</code>.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void toStreamEntryNodeStreamReplaySupportTest() {}

    /**
     * Test case when target list stream contains child with name 'replay-log-creation-time'. Test is catching
     * <code>IllegalStateException</code>.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void toStreamEntryNodeStreamReplayLogTest() {}

    /**
     * Test case when target list stream contains child with name 'events'. Test is catching
     * <code>IllegalStateException</code>.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void toStreamEntryNodeStreamEventsTest() {}

    /**
     * Verify if correct modules were loaded into Restconf module by comparison with modules from <code>SchemaContext</code>
     * @param expectedModules Modules from <code>SchemaContext</code>
     * @param loadedModules Loaded modules into Restconf module
     */
    private void verifyModules(Set<Module> expectedModules, Map<String, String> loadedModules) {
        assertEquals("Number of loaded modules is not as expected", expectedModules.size(), loadedModules.size());
        for (Module m : expectedModules) {
            String name = m.getName();

            String revision = loadedModules.get(name);
            assertNotNull("Expected module not found", revision);
            assertEquals("Not correct revision of loaded module",
                    SimpleDateFormatUtil.getRevisionFormat().format(m.getRevision()), revision);

            loadedModules.remove(name);
        }
    }

    /**
     * Verify loaded streams. Compare loaded stream names to expected stream names.
     * @param streams Streams to be verified
     */
    private void verifyStreams(final List<String> streams) {
        assertEquals("Loaded number of streams is not correct", expectedStreams.size(), streams.size());

        for (final String s : expectedStreams) {
            assertTrue("Stream " + s + " should be found in restconf module", streams.contains(s));
            streams.remove(s);
        }
    }
}
