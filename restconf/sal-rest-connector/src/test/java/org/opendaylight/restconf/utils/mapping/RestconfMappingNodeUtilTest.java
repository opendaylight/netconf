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
import static org.junit.Assert.fail;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.Draft11.RestconfModule;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link RestconfMappingNodeUtil}
 */
public class RestconfMappingNodeUtilTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final List<String> expectedStreams = Arrays.asList(new String[] {"stream-1", "stream-2", "stream-3"});
    private Set<Module> modules;
    private SchemaContext schemaContext;

    @Before
    public void setup() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules/restconf-module-testing");
        modules = TestRestconfUtils.loadSchemaContext("/modules").getModules();

        // create streams
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(0));
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(1));
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(2));
    }

    @Test //fixme
    public void restconfMappingNodeTest() {
        final MapNode mapNode = RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("ietf-restconf"), schemaContext.getModules());
        assertNotNull("Mapping of modules should be successful", mapNode);

        // verify loaded modules
        final Map<String, String> expectedModules = new HashMap<>();
        final Iterator<MapEntryNode> iterator = mapNode.getValue().iterator();
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

    @Test //fixme
    public void toStreamEntryNodeTest() throws Exception {
        MapEntryNode mapEntryNode = RestconfMappingNodeUtil.toStreamEntryNode("module1", null);
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

        verifyStreams(loadedStreams);
    }

    /**
     * Test mapping modules to list with <code>null</code> Restconf module. Test fails with
     * <code>NullPointerException</code>
     */
    @Test
    public void restconfMappingNodeMissingRestconfModuleTest() {
        thrown.expect(NullPointerException.class);
        RestconfMappingNodeUtil.restconfMappingNode(null, this.modules);
    }

    /**
     * Try to map modules into module list when Restconf module is available but does not contain node
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE}. <code>RestconfDocumentedException</code> is expected and error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void restconfMappingNodeMissingModuleListNegativeTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-list-module"), this.modules);
            fail("Test should fail due to missing "
                    + RestconfModule.MODULE_LIST_SCHEMA_NODE
                    + " node in Restconf module");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to map modules into module list when Restconf module is available but contains node
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} but it is not of type list. <code>IllegalStateException</code>
     * should be returned.
     */
    @Test
    public void restconfMappingNodeIllegalModuleListNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("restconf-module-with-illegal-list-module"), this.modules);
    }

    /**
     * Map <code>null</code> set of modules to module list. <code>NullPointerException</code> is expected.
     */
    @Test
    public void restconfMappingNodeNullModulesTest() {
        thrown.expect(NullPointerException.class);
        RestconfMappingNodeUtil.restconfMappingNode(getTestingRestconfModule("ietf-restconf"), null);
    }

    /**
     * Try to map modules into list module of Restconf module when Restconf module does not contain grouping
     * {@link RestconfModule#RESTCONF_GROUPING_SCHEMA_NODE}. <code>RestconfDocumentedException</code> is expected and
     * error type, error tag and error status code are compared to expected values.
     */
    @Test
    public void restconfMappingNodeNoRestconfGroupingTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-grouping-restconf"), modules);
            fail("Test should fail due to missing "
                    + RestconfModule.RESTCONF_GROUPING_SCHEMA_NODE
                    + " grouping in Restconf module");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to map modules into list module of Restconf module when Restconf module does not contain any grouping.
     * Test is catching <code>RestconfDocumentedException</code> and checking error type and error tag.
     */
    @Ignore  //// FIXME: 16. 6. 2016
    @Test(expected = RestconfDocumentedException.class)
    public void restconfMappingNodeNoGroupingsTest() {}

    /**
     * Test when there is a grouping with name {@link RestconfModule#RESTCONF_GROUPING_SCHEMA_NODE} in Restconf
     * module but contains no child nodes.
     */
    @Test
    public void restconfMappingNodeRestconfGroupingNoChildTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-empty-grouping-restconf"), modules);
            fail();
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**FIXME - the same like not found
     * Test when container restconf is not the first child in grouping restconf.
     */

    /**
     * Module list in Restconf module does not contain any child nodes. Test fails with
     * <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Test
    public void restconfMappingNodeModuleListNoChilds() {}

    /**
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module does not contain any child with name 'name'.
     * Test fails with <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Test
    public void restconfMappingNodeMissingNameNegativeTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-leaf-name-in-list-module"), modules);
            fail("Test should fail due to missing leaf name in "
                    + RestconfModule.MODULE_LIST_SCHEMA_NODE
                    + " node");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module does not contain any child with name
     * 'revision'. Test fails with <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Test
    public void restconfMappingNodeMissingRevisionNegativeTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-leaf-revision-in-list-module"), modules);
            fail("Test should fail due to missing leaf name in "
                    + RestconfModule.MODULE_LIST_SCHEMA_NODE
                    + " node");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module does not contain any child with name
     * 'namespace'. Test fails with <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Test
    public void restconfMappingNodeMissingNamespaceNegativeTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-leaf-namespace-in-list-module"), modules);
            fail("Test should fail due to missing leaf name in "
                    + RestconfModule.MODULE_LIST_SCHEMA_NODE
                    + " node");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module does not contain any child with name
     * 'features'. Test fails with <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Test
    public void restconfMappingNodeMissingFeaturesTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule(""), modules);
            fail();
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module contains child with name 'name' but it is not
     * of type leaf. Test fails with <code>IllegalStateException</code>.
     */
    @Test
    public void restconfMappingNodeIllegalNameNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("restconf-module-with-illegal-leaf-name-in-list-module"), modules);
    }

    /**
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module contains child with name 'revision' but it is
     * not of type leaf. Test fails with <code>RestconfDocumentedException</code> checking error type and error tag.
     */
    @Test
    public void restconfMappingNodeIllegalRevisionNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("restconf-module-with-illegal-leaf-revison-in-list-module"), modules);
    }

    /**
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module contains child with name 'namespace' but it is
     * not of type leaf. Test fails with <code>IllegalStateException</code>.
     */
    @Test
    public void restconfMappingNodeIllegalNamespaceNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("restconf-module-with-illegal-leaf-namespace-in-list-module"), modules);
    }

    /**
     * Module list in Restconf module contains child with name 'features' but it is not of type leaf-list. Test fails
     * with <code>IllegalStateException</code>.
     */
    @Test(expected = IllegalStateException.class)
    public void restconfMappingNodeFeaturesTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(getTestingRestconfModule(""), modules);
    }

    /**
     * Try to map streams when target <code>DataSchemaNode</code> is null. Test is expected to fail catching
     * <code>NullPointerException</code>.
     */
    @Test
    public void toStreamEntryNodeNullSchemaNodeTest() {
        thrown.expect(NullPointerException.class);
        RestconfMappingNodeUtil.restconfMappingNode(getTestingRestconfModule(""), modules);
    }

    /**
     * Test trying to map streams to <code>DataSchemaNode</code> which is not of type list. Test is expected to fail
     * catching <code>IllegalStateException</code>.
     */
    @Test
    public void toStreamEntryNodeWrongTypeOfSchemaNodeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(getTestingRestconfModule(""), modules);
    }

    /**
     * Test case with target <code>DataSchemaNode</code> which does not contain any child nodes. Test is catching
     * <code>RestconfDocumentedException</code> checking error type and error tag.
     */
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

    /**
     * There are multiple testing Restconf modules for different test cases. It is possible to distinguish them by
     * name or by namespace. This method is looking for Restconf test module by its name.
     * @param s Testing Restconf module name
     * @return Restconf module
     */
    private Module getTestingRestconfModule(final String s) {
        return this.schemaContext.findModuleByName(s, Draft11.RestconfModule.IETF_RESTCONF_QNAME.getRevision());
    }
}
