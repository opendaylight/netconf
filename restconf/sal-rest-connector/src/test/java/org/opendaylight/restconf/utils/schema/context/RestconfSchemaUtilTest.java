/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.schema.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Unit tests for {@link RestconfSchemaUtil}
 */
public class RestconfSchemaUtilTest {
    private SchemaContext schemaContext;
    private org.opendaylight.yangtools.yang.model.api.Module restconfModule;

    @Before
    public void setup() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        restconfModule = schemaContext.findModuleByName(Draft11.RestconfModule.NAME,
                SimpleDateFormatUtil.getRevisionFormat().parse(Draft11.RestconfModule.REVISION));
    }

    /**
     * Positive test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link org.opendaylight.restconf.Draft11.RestconfModule#MODULE_LIST_SCHEMA_NODE} when this node can be found.
     */
    @Test
    public void getRestconfSchemaNodeListModuleTest() {
        DataSchemaNode dataSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                Draft11.RestconfModule.MODULE_LIST_SCHEMA_NODE);
        assertNotNull("Module list schema node should be found", dataSchemaNode);
    }

    /**
     * Positive test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link org.opendaylight.restconf.Draft11.MonitoringModule#STREAM_LIST_SCHEMA_NODE} when this node can be found.
     */
    @Test
    public void getRestconfSchemaNodeListStreamTest() {
        DataSchemaNode dataSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                Draft11.MonitoringModule.STREAM_LIST_SCHEMA_NODE);
        assertNotNull("Stream list schema node should be found", dataSchemaNode);
    }

    // new test plan

    // POSITIVE

    /**
     * Positive test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link org.opendaylight.restconf.Draft11.RestconfModule#MODULES_CONTAINER_SCHEMA_NODE} when this node can be
     * found.
     */
    @Ignore
    @Test
    public void getRestconfSchemaNodeContainerModulesTest() {}

    /**
     * Positive test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link org.opendaylight.restconf.Draft11.MonitoringModule#STREAMS_CONTAINER_SCHEMA_NODE} when this node can be
     * found.
     */
    @Ignore
    @Test
    public void getRestconfSchemaNodeContainerStreamsTest() {}

    // NEGATIVE
    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module when Restconf module is
     * <code>null</code>. Test is expected to fail catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void getRestconfSchemaNodeNullRestconfModule() {}

    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module when name of the schema node name is
     * <code>null</code>. Test is expected to fail catching <code>RestconfDocumentedException</code> and checking
     * expected error message, error type and error tag.
     */
    @Ignore
    @Test
    public void getRestconfSchemaNodeNullSchemaNodeName() {}

    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module when name of the schema node name
     * references to not existing node. Test is expected to fail catching code>RestconfDocumentedException</code> and
     * checking expected error message, error type and error tag.
     */
    @Ignore
    @Test
    public void getRestconfSchemaNodeNotExistingSchemaNodeName() {}

    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link org.opendaylight.restconf.Draft11.RestconfModule#MODULES_CONTAINER_SCHEMA_NODE} when this node cannot
     * be found. <code>NullPointerException</code> is expected.
     */
    @Ignore
    @Test
    public void getRestconfSchemaNodeContainerModulesNegativeTest() {}

    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link org.opendaylight.restconf.Draft11.RestconfModule#MODULE_LIST_SCHEMA_NODE} when this node cannot
     * be found. <code>NullPointerException</code> is expected.
     */
    @Ignore
    @Test
    public void getRestconfSchemaNodeListModuleNegativeTest() {}

    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link org.opendaylight.restconf.Draft11.MonitoringModule#STREAMS_CONTAINER_SCHEMA_NODE} when this node cannot
     * be found. <code>NullPointerException</code> is expected.
     */
    @Ignore
    @Test
    public void getRestconfSchemaNodeContainerStreamsNegativeTest() {}

    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link org.opendaylight.restconf.Draft11.MonitoringModule#STREAM_LIST_SCHEMA_NODE} when this node cannot
     * be found. <code>NullPointerException</code> is expected.
     */
    @Ignore
    @Test
    public void getRestconfSchemaNodeListStreamNegativeTest() {}

   // FIXME add test to verify Restconf module?

    // ---

    /**
     * Positive test trying to find <code>DataSchemaNode</code> of
     * {@link org.opendaylight.restconf.Draft11.RestconfModule#RESTCONF_GROUPING_SCHEMA_NODE} in Restconf module
     * groupings collection.
     */
    @Test
    public void findSchemaNodeInCollectionTest() {
        SchemaNode schemaNode = RestconfSchemaUtil.findSchemaNodeInCollection(restconfModule.getGroupings(),
                Draft11.RestconfModule.RESTCONF_GROUPING_SCHEMA_NODE);
        assertNotNull("Restconf grouping schema node should be found", schemaNode);
    }

    /**
     * Negative test trying to find <code>DataSchemaNode</code> of not existing schema node name in Restconf module
     * grouping collection. Test is expected to fail catching <code>RestconfDocumentedException</code> and checking
     * for correct error type, error tag and error status code.
     */
    @Test
    public void findSchemaNodeInCollectionNegativeTest() {
        try {
            RestconfSchemaUtil.findSchemaNodeInCollection(restconfModule.getGroupings(), "not-existing");
            fail("Test should fail due to missing not-existing grouping in collection");
        } catch (RestconfDocumentedException e) {
            assertEquals(RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(RestconfError.ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals(404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    // new tests

    /**
     * Negative test trying to find <code>DataSchemaNode</code> of existing schema node name in <code>null</code>
     * collection. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void findSchemaNodeInCollectionNullCollection() {}

    /**
     * Negative test trying to find <code>DataSchemaNode</code> of schema node name in empty collection. Test is
     * expected to fail with <code>RestconfDocumentedException</code>. Error type, error tag and error status code
     * are checked.
     */
    @Ignore
    @Test
    public void findSchemaNodeInCollectionEmptyCollection() {}

    /**
     * Negative test trying to find <code>DataSchemaNode</code> of <code>null</code> schema node name in Restconf module
     * groupings collection. Test is expected to fail with <code>RestconfDocumentedException</code>. Error type, error
     * tag and error status code are checked for expected values.
     */
    @Ignore
    @Test
    public void findSchemaNodeInCollectionNullSchemaNodeName() {}

}
