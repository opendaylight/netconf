/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.Draft16;
import org.opendaylight.restconf.Draft16.MonitoringModule;
import org.opendaylight.restconf.Draft16.RestconfModule;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link RestconfMappingNodeUtil}
 */
public class RestconfMappingNodeUtilTest {
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
    private Set<DataSchemaNode> allStreamChildNodes;

    @BeforeClass
    public static void loadTestSchemaContextAndModules() throws Exception {
        RestconfMappingNodeUtilTest.schemaContext = TestRestconfUtils.loadSchemaContext(
                "/modules/restconf-module-testing");
        RestconfMappingNodeUtilTest.modules = TestRestconfUtils.loadSchemaContext("/modules").getModules();
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(this.leafName.getQName()).thenReturn(QName.create("", RestconfMappingNodeConstants.NAME));
        when(this.leafDescription.getQName()).thenReturn(QName.create("", RestconfMappingNodeConstants.DESCRIPTION));
        when(this.leafReplaySupport.getQName()).thenReturn(
                QName.create("", RestconfMappingNodeConstants.REPLAY_SUPPORT));
        when(this.leafReplayLog.getQName()).thenReturn(QName.create(RestconfMappingNodeConstants.REPLAY_LOG));
        when(this.leafEvents.getQName()).thenReturn(QName.create("", RestconfMappingNodeConstants.EVENTS));

        this.allStreamChildNodes = Sets.newHashSet(
                this.leafName, this.leafDescription, this.leafReplaySupport, this.leafReplayLog, this.leafEvents);
    }

    /**
     * Test of writing modules into {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} and checking if modules were
     * correctly written.
     */
    @Test
    public void restconfMappingNodeTest() {
        // write modules into list module in Restconf
        final MapNode modules = RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("ietf-restconf"), RestconfMappingNodeUtilTest.modules);

        // verify loaded modules
        verifyLoadedModules(modules);
    }

    /**
     * Positive test of writing one stream to {@link MonitoringModule#STREAM_LIST_SCHEMA_NODE} and checking if stream
     * was correctly written.
     */
    @Test
    public void toStreamEntryNodeTest() {
        // test stream name
        final String stream1 = "stream-1";

        // get list stream node from Restconf module
        final ListSchemaNode listStream = (ListSchemaNode) RestconfSchemaUtil.getRestconfSchemaNode(
                getTestingRestconfModule("ietf-restconf"), MonitoringModule.STREAM_LIST_SCHEMA_NODE);

        // write stream to list stream node
        final MapEntryNode mapEntryNode = RestconfMappingNodeUtil.toStreamEntryNode(stream1, listStream);

        // verify
        verifyStream(stream1, mapEntryNode);
    }

    /**
     * Test mapping modules to list with <code>null</code> Restconf module. Test fails with
     * <code>NullPointerException</code>.
     */
    @Test
    public void restconfMappingNodeMissingRestconfModuleNegativeTest() {
        thrown.expect(NullPointerException.class);
        RestconfMappingNodeUtil.restconfMappingNode(null, RestconfMappingNodeUtilTest.modules);
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
                    getTestingRestconfModule("restconf-module-with-missing-list-module"),
                    RestconfMappingNodeUtilTest.modules);
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
     * Try to map modules into module list when Restconf module is available and contains node
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} but it is not of type list. <code>IllegalStateException</code>
     * should be returned.
     */
    @Test
    public void restconfMappingNodeIllegalModuleListNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("restconf-module-with-illegal-list-module"),
                RestconfMappingNodeUtilTest.modules);
    }

    /**
     * Map <code>null</code> set of modules to module list. <code>NullPointerException</code> is expected.
     */
    @Test
    public void restconfMappingNodeNullModulesNegativeTest() {
        thrown.expect(NullPointerException.class);
        RestconfMappingNodeUtil.restconfMappingNode(getTestingRestconfModule("ietf-restconf"), null);
    }

    /**
     * Try to map modules into list module of Restconf module when Restconf module does not contain grouping
     * {@link RestconfModule#RESTCONF_GROUPING_SCHEMA_NODE}. <code>RestconfDocumentedException</code> is expected and
     * error type, error tag and error status code are compared to expected values.
     */
    @Test
    public void restconfMappingNodeNoRestconfGroupingNegativeTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-grouping-restconf"),
                    RestconfMappingNodeUtilTest.modules);
            fail("Test should fail due to missing "
                    + RestconfModule.RESTCONF_GROUPING_SCHEMA_NODE
                    + " grouping in Restconf module groupings");
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
     * Test is catching <code>RestconfDocumentedException</code> and checking error type, error and error status code.
     */
    @Test
    public void restconfMappingNodeNoGroupingsNegativeTest() {
        // prepare conditions
        final Module mockRestconfModule = mock(Module.class);
        when(mockRestconfModule.getGroupings()).thenReturn(Sets.newHashSet());

        // test
        try {
            RestconfMappingNodeUtil.restconfMappingNode(mockRestconfModule, RestconfMappingNodeUtilTest.modules);
            fail("Test should fail due to no child nodes in Restconf grouping");
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
     * Test when there is a grouping with name {@link RestconfModule#RESTCONF_GROUPING_SCHEMA_NODE} in Restconf
     * module but contains no child nodes. <code>NoSuchElementException</code> is expected.
     */
    @Test
    public void restconfMappingNodeRestconfGroupingNoChildNegativeTest() {
        // prepare conditions
        final Module mockRestconfModule = mock(Module.class);
        final GroupingDefinition mockRestconfGrouping = mock(GroupingDefinition.class);
        when(mockRestconfGrouping.getQName()).thenReturn(QName.create(
                "", RestconfModule.RESTCONF_GROUPING_SCHEMA_NODE));
        when(mockRestconfModule.getGroupings()).thenReturn(Sets.newHashSet(mockRestconfGrouping));
        when(mockRestconfGrouping.getChildNodes()).thenReturn(Sets.newHashSet());

        // test
        thrown.expect(NoSuchElementException.class);
        RestconfMappingNodeUtil.restconfMappingNode(mockRestconfModule, RestconfMappingNodeUtilTest.modules);
    }

    /**
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module does not contain any child with name
     * {@link RestconfMappingNodeConstants#NAME}. Test fails with <code>RestconfDocumentedException</code> checking
     * error type, error tag and error status code.
     */
    @Test
    public void restconfMappingNodeMissingNameNegativeTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-leaf-name-in-list-module"),
                    RestconfMappingNodeUtilTest.modules);
            fail("Test should fail due to missing leaf "
                    + RestconfMappingNodeConstants.NAME
                    + " in "
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
     * {@link RestconfMappingNodeConstants#REVISION}. Test fails with <code>RestconfDocumentedException</code> checking
     * error type, error tag and error status code.
     */
    @Test
    public void restconfMappingNodeMissingRevisionNegativeTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-leaf-revision-in-list-module"),
                    RestconfMappingNodeUtilTest.modules);
            fail("Test should fail due to missing leaf "
                    + RestconfMappingNodeConstants.REVISION
                    + " in "
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
     * {@link RestconfMappingNodeConstants#NAMESPACE}. Test fails with <code>RestconfDocumentedException</code>
     * checking error type, error tag and error status code.
     */
    @Test
    public void restconfMappingNodeMissingNamespaceNegativeTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-leaf-namespace-in-list-module"),
                    RestconfMappingNodeUtilTest.modules);
            fail("Test should fail due to missing leaf "
                    + RestconfMappingNodeConstants.NAMESPACE
                    + " in "
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
     * {@link RestconfMappingNodeConstants#FEATURE}. Test fails with <code>RestconfDocumentedException</code> checking
     * error type, error tag and error status code.
     */
    @Test
    public void restconfMappingNodeMissingFeaturesNegativeTest() {
        try {
            RestconfMappingNodeUtil.restconfMappingNode(
                    getTestingRestconfModule("restconf-module-with-missing-leaf-list-feature-in-list-module"),
                    RestconfMappingNodeUtilTest.modules);
            fail("Test should fail due to missing leaf "
                    + RestconfMappingNodeConstants.FEATURE
                    + " in "
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
     *
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module contains child with name
     * {@link RestconfMappingNodeConstants#NAME} but it is not of type leaf. Test fails with
     * <code>IllegalStateException</code>.
     */
    @Test
    public void restconfMappingNodeIllegalNameNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("restconf-module-with-illegal-leaf-name-in-list-module"),
                RestconfMappingNodeUtilTest.modules);
    }

    /**
     *
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module contains child with name
     * {@link RestconfMappingNodeConstants#REVISION} but it is not of type leaf. Test fails with
     * <code>IllegalStateException</code>.
     */
    @Test
    public void restconfMappingNodeIllegalRevisionNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("restconf-module-with-illegal-leaf-revision-in-list-module"),
                RestconfMappingNodeUtilTest.modules);
    }

    /**
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} in Restconf module contains child with name
     * {@link RestconfMappingNodeConstants#NAMESPACE} but it is not of type leaf. Test fails with
     * <code>IllegalStateException</code>.
     */
    @Test
    public void restconfMappingNodeIllegalNamespaceNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("restconf-module-with-illegal-leaf-namespace-in-list-module"),
                RestconfMappingNodeUtilTest.modules);
    }

    /**
     * Module list in Restconf module contains child with name {@link RestconfMappingNodeConstants#FEATURE} but it is
     * not of type leaf-list. Test fails with <code>IllegalStateException</code>.
     */
    @Test
    public void restconfMappingNodeIllegalFeatureNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.restconfMappingNode(
                getTestingRestconfModule("restconf-module-with-illegal-leaf-list-feature-in-list-module"),
                RestconfMappingNodeUtilTest.modules);
    }

    /**
     * Try to map streams when {@link MonitoringModule#STREAM_LIST_SCHEMA_NODE} is <code>null</code>.
     * Test is expected to fail catching <code>IllegalStateException</code>.
     */
    @Test
    public void toStreamEntryNodeNullListStreamNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.toStreamEntryNode("stream-1", null);
    }

    /**
     * Test trying to map streams to {@link MonitoringModule#STREAM_LIST_SCHEMA_NODE} which is not of type list.
     * Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void toStreamEntryNodeIllegalListStreamNegativeTest() {
        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.toStreamEntryNode("stream-1", mock(LeafSchemaNode.class));
    }

    /**
     * Test case with target {@link MonitoringModule#STREAM_LIST_SCHEMA_NODE} which does not contain any child nodes.
     * Test is catching <code>RestconfDocumentedException</code> and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void toStreamEntryNodeSchemaNodeWithoutChildsNegativeTest() {
        final ListSchemaNode mockListNode = mock(ListSchemaNode.class);
        when(mockListNode.getChildNodes()).thenReturn(Collections.EMPTY_SET);

       try {
           RestconfMappingNodeUtil.toStreamEntryNode("stream-1", mockListNode);
           fail("Test should fail due to no child nodes in"
                   + MonitoringModule.STREAM_LIST_SCHEMA_NODE
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
     * Test case when target list stream does not contain child with name {@link RestconfMappingNodeConstants#NAME}.
     * Test is catching <code>RestconfDocumentedException</code> and error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    public void toStreamEntryNodeMissingStreamNameNegativeTest() {
        prepareMockListWithMissingLeaf(this.leafName);

        try {
            RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
            fail("Test should fail due to missing "
                    + RestconfMappingNodeConstants.NAME
                    + " node in " + MonitoringModule.STREAM_LIST_SCHEMA_NODE);
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
     * Test case when target list stream does not contain child with name
     * {@link RestconfMappingNodeConstants#DESCRIPTION}. Test is catching <code>RestconfDocumentedException</code> and
     * checking error type and error tag.
     */
    @Test
    public void toStreamEntryNodeMissingStreamDescriptionNegativeTest() {
        prepareMockListWithMissingLeaf(this.leafDescription);

        try {
            RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
            fail("Test should fail due to missing "
                    + RestconfMappingNodeConstants.DESCRIPTION
                    + " node in " + MonitoringModule.STREAM_LIST_SCHEMA_NODE);
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
     * Test case when target list stream does not contain child with name
     * {@link RestconfMappingNodeConstants#REPLAY_SUPPORT}. Test is catching <code>RestconfDocumentedException</code>
     * and checking error type and error tag.
     */
    @Test
    public void toStreamEntryNodeMissingStreamReplaySupportNegativeTest() {
        prepareMockListWithMissingLeaf(this.leafReplaySupport);

        try {
            RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
            fail("Test should fail due to missing "
                    + RestconfMappingNodeConstants.REPLAY_SUPPORT
                    + " node in " + MonitoringModule.STREAM_LIST_SCHEMA_NODE);
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
     * Test case when target list stream does not contain child with name
     * {@link RestconfMappingNodeConstants#REPLAY_LOG}. Test is catching <code>RestconfDocumentedException</code> and
     * checking error type and error tag.
     */
    @Test
    public void toStreamEntryNodeMissingStreamReplayLogNegativeTest() {
        prepareMockListWithMissingLeaf(this.leafReplayLog);

        try {
            RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
            fail("Test should fail due to missing "
                    + RestconfMappingNodeConstants.REPLAY_LOG
                    + " node in " + MonitoringModule.STREAM_LIST_SCHEMA_NODE);
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
     * Test case when target list stream does not contain child with name {@link RestconfMappingNodeConstants#EVENTS}.
     * Test is catching <code>RestconfDocumentedException</code> and checking error type, error tag and error status
     * code.
     */
    @Test
    public void toStreamEntryNodeMissingStreamEventsNegativeTest() {
        prepareMockListWithMissingLeaf(this.leafEvents);

        try {
            RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
            fail("Test should fail due to missing "
                    + RestconfMappingNodeConstants.EVENTS
                    + " node in " + MonitoringModule.STREAM_LIST_SCHEMA_NODE);
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
     * Test case when target list stream contains child with name {@link RestconfMappingNodeConstants#NAME}. Test is
     * expecting <code>IllegalStateException</code>.
     */
    @Test
    public void toStreamEntryNodeStreamNameNegativeTest() {
        prepareMockListWithIllegalLeaf(this.leafName);

        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
    }

    /**
     * Test case when target list stream contains child with name {@link RestconfMappingNodeConstants#DESCRIPTION}.
     * Test is expecting <code>IllegalStateException</code>.
     */
    @Test
    public void toStreamEntryNodeStreamDescriptionNegativeTest() {
        prepareMockListWithIllegalLeaf(this.leafDescription);

        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
    }

    /**
     * Test case when target list stream contains child with name {@link RestconfMappingNodeConstants#REPLAY_SUPPORT}.
     * Test is expecting <code>IllegalStateException</code>.
     */
    @Test
    public void toStreamEntryNodeStreamReplaySupportNegativeTest() {
        prepareMockListWithIllegalLeaf(this.leafReplaySupport);

        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
    }

    /**
     * Test case when target list stream contains child with name {@link RestconfMappingNodeConstants#REPLAY_LOG}.
     * Test is expecting <code>IllegalStateException</code>.
     */
    @Test
    public void toStreamEntryNodeStreamReplayLogNegativeTest() {
        prepareMockListWithIllegalLeaf(this.leafReplayLog);

        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
    }

    /**
     * Test case when target list stream contains child with name {@link RestconfMappingNodeConstants#EVENTS}. Test is
     * expecting <code>IllegalStateException</code>.
     */
    @Test
    public void toStreamEntryNodeStreamEventsNegativeTest() {
        prepareMockListWithIllegalLeaf(this.leafEvents);

        thrown.expect(IllegalStateException.class);
        RestconfMappingNodeUtil.toStreamEntryNode("stream-1", this.mockStreamList);
    }

    /**
     * Utils
     */

    /**
     * Verify loaded modules from Restconf module
     * @param modules Returned modules node
     */
    private void verifyLoadedModules(final MapNode modules) {
        final Iterator<MapEntryNode> iterator = modules.getValue().iterator();
        final Map<String, String> loadedModules = new HashMap<>();

        while (iterator.hasNext()) {
            final Iterator entries = ((AbstractImmutableDataContainerAttrNode) iterator.next())
                    .getChildren().entrySet().iterator();

            String name = null;
            String revision = null;

            boolean notAllowedKey = false;
            while (entries.hasNext()) {
                final Entry e = ((AbstractMap.SimpleImmutableEntry) entries.next());
                final String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                switch (key) {
                    case RestconfMappingNodeConstants.NAME:
                        name = (String) ((LeafNode) e.getValue()).getValue();
                        break;
                    case RestconfMappingNodeConstants.REVISION:
                        revision = (String) ((LeafNode) e.getValue()).getValue();
                        break;
                    case RestconfMappingNodeConstants.NAMESPACE:
                        // fall through
                    case RestconfMappingNodeConstants.FEATURE:
                        break;
                    default:
                        notAllowedKey = true;
                        break;
                }
            }

            assertFalse("Not allowed key in list module found", notAllowedKey);
            loadedModules.put(name, revision);
        }

        verifyLoadedModules(RestconfMappingNodeUtilTest.modules, loadedModules);
    }

    /**
     * Verify if correct modules were loaded into Restconf module by comparison with modules from
     * <code>SchemaContext</code>.
     * @param expectedModules Modules from <code>SchemaContext</code>
     * @param loadedModules Loaded modules into Restconf module
     */
    private final void verifyLoadedModules(final Set<Module> expectedModules,
                                           final Map<String, String> loadedModules) {
        assertEquals("Number of loaded modules is not as expected", expectedModules.size(), loadedModules.size());
        for (final Module m : expectedModules) {
            final String name = m.getName();

            final String revision = loadedModules.get(name);
            assertNotNull("Expected module not found", revision);
            assertEquals("Not correct revision of loaded module",
                    SimpleDateFormatUtil.getRevisionFormat().format(m.getRevision()), revision);

            loadedModules.remove(name);
        }
    }

    /**
     * Verify if a stream was correctly written into {@link MonitoringModule#STREAM_LIST_SCHEMA_NODE} node in Restconf
     * module.
     * @param streamName Expected stream name
     * @param streamNode Writetn strem node from Restconf module
     */
    private final void verifyStream(final String streamName, final MapEntryNode streamNode) {
        assertNotNull("Stream node can not be null", streamNode);
        final Iterator entries = ((AbstractImmutableDataContainerAttrNode) streamNode)
                .getChildren().entrySet().iterator();
        boolean notAllowedKey = false;

        while (entries.hasNext()) {
            final Entry e = ((AbstractMap.SimpleImmutableEntry) entries.next());
            final String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

            switch (key) {
                case RestconfMappingNodeConstants.NAME :
                    assertEquals("Stream name value is not as expected",
                            streamName, ((LeafNode) e.getValue()).getValue());
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
                    notAllowedKey = true;
                    break;
            }
        }

        assertFalse("Not allowed key in list stream found", notAllowedKey);
    }

    /**
     * There are multiple testing Restconf modules for different test cases. It is possible to distinguish them by
     * name or by namespace. This method is looking for Restconf test module by its name.
     * @param s Testing Restconf module name
     * @return Restconf module
     */
    private Module getTestingRestconfModule(final String s) {
        return RestconfMappingNodeUtilTest.schemaContext.findModuleByName(
                s, Draft16.RestconfModule.IETF_RESTCONF_QNAME.getRevision());
    }

    /**
     * Updates {@link this#mockStreamList} to NOT contains specified leaf.
     * @param leaf Leaf to be missing
     */
    private void prepareMockListWithMissingLeaf(final LeafSchemaNode leaf) {
        // prepare set of leaf without selected leaf
        final Set childLeafs = new HashSet<>(this.allStreamChildNodes);
        childLeafs.remove(leaf);

        // mock list leaf nodes
        when(this.mockStreamList.getChildNodes()).thenReturn(childLeafs);
    }

    /**
     * Updates {@link this#mockStreamList} to contains specified leaf which is not of type {@link LeafSchemaNode}.
     * @param leaf Leaf to be changes
     */
    private void prepareMockListWithIllegalLeaf(final LeafSchemaNode leaf) {
        // prepare set of leaf without selected leaf
        final Set childLeafs = new HashSet<>(this.allStreamChildNodes);
        childLeafs.remove(leaf);

        // add leaf-list with the same local name as removed leaf
        final String localName = leaf.getQName().getLocalName();
        final LeafListSchemaNode mockLeafList = mock(LeafListSchemaNode.class);
        when(mockLeafList.getQName()).thenReturn(QName.create("", localName));
        childLeafs.add(mockLeafList);

        // mock list leaf nodes
        when(this.mockStreamList.getChildNodes()).thenReturn(childLeafs);
    }
}
