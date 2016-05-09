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

import java.util.NoSuchElementException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link RestconfMappingNodeUtil}
 */
public class RestconfMappingNodeUtilTest {
    private SchemaContext schemaContext;
    private org.opendaylight.yangtools.yang.model.api.Module restconfModule;
    private DataSchemaNode restconfSchemaNode;

    @Before
    public void setup() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        restconfModule = schemaContext.findModuleByName("ietf-restconf",
                SimpleDateFormatUtil.getRevisionFormat().parse("2013-10-19"));
        restconfSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                Draft11.MonitoringModule.STREAM_LIST_SCHEMA_NODE);
    }

    @Test
    public void restconfMappingNodeTest() {
        MapNode mapNode = RestconfMappingNodeUtil.restconfMappingNode(restconfModule, schemaContext.getModules());
        assertNotNull("Mapping of modules should be successful", mapNode);

        assertEquals("Looking for module list schema node",
                Draft11.RestconfModule.MODULE_LIST_SCHEMA_NODE, mapNode.getNodeType().getLocalName());

        QNameModule restconfModule = mapNode.getNodeType().getModule();
        assertNotNull("Restconf module should be found", restconfModule);
        assertEquals("Expected correct Restconf module revision",
                Draft11.RestconfModule.REVISION, restconfModule.getFormattedRevision());
        assertEquals("Expected correct Restconf module namespace",
                Draft11.RestconfModule.NAMESPACE, restconfModule.getNamespace().toString());
    }

    @Test
    public void toStreamEntryNodeTest() {
        MapEntryNode mapEntryNode = RestconfMappingNodeUtil.toStreamEntryNode("module1", restconfSchemaNode);
        assertNotNull("Map entry node should be created", mapEntryNode);

        assertEquals("Looking for stream list schema node",
                Draft11.MonitoringModule.STREAM_LIST_SCHEMA_NODE, mapEntryNode.getNodeType().getLocalName());

        QNameModule restconfModule = mapEntryNode.getNodeType().getModule();
        assertNotNull("Restconf module should be found", restconfModule);
        assertEquals("Expected correct Restconf module revision",
                Draft11.RestconfModule.REVISION, restconfModule.getFormattedRevision());
        assertEquals("Expected correct Restconf module namespace",
                Draft11.RestconfModule.NAMESPACE, restconfModule.getNamespace().toString());
    }

    // ** new test plan

    /**
     * Test mapping modules to list without Restconf module. Test fails with <code>NullPointerException</code>
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void restconfMappingNodeMissingRestconfModuleTest() {}

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
    @Ignore
    @Test(expected = NullPointerException.class)
    public void restconfMappingNodeNullModulesTest() {}

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
}
