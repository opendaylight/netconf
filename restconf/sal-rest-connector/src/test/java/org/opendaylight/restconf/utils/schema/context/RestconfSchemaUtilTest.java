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
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.Draft18;
import org.opendaylight.restconf.Draft18.MonitoringModule;
import org.opendaylight.restconf.Draft18.RestconfModule;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Unit tests for {@link RestconfSchemaUtil}
 */
public class RestconfSchemaUtilTest {
    // schema with testing modules
    private SchemaContext schemaContext;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() throws Exception {
        this.schemaContext = TestRestconfUtils.loadSchemaContext("/modules/restconf-module-testing");
    }

    /**
     * Positive test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link RestconfModule#MODULE_LIST_SCHEMA_NODE} when this node can be found.
     */
    @Test
    public void getRestconfSchemaNodeListModuleTest() {
        final DataSchemaNode dataSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(
                getTestingRestconfModule("ietf-restconf"),
                RestconfModule.MODULE_LIST_SCHEMA_NODE);

        assertNotNull("Existing schema node "+ RestconfModule.MODULE_LIST_SCHEMA_NODE + " should be found",
                dataSchemaNode);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getLocalName(), RestconfModule.MODULE_LIST_SCHEMA_NODE);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getNamespace().toString(), RestconfModule.NAMESPACE);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getFormattedRevision(), RestconfModule.REVISION);
    }

    /**
     * Positive test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link MonitoringModule#STREAM_LIST_SCHEMA_NODE} when this node can be found.
     */
    @Test
    public void getRestconfSchemaNodeListStreamTest() {
        final DataSchemaNode dataSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(
                getTestingRestconfModule("ietf-restconf"),
                MonitoringModule.STREAM_LIST_SCHEMA_NODE);

        assertNotNull("Existing schema node " + MonitoringModule.STREAM_LIST_SCHEMA_NODE + " should be found",
                dataSchemaNode);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getLocalName(), MonitoringModule.STREAM_LIST_SCHEMA_NODE);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getNamespace().toString(), RestconfModule.NAMESPACE);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getFormattedRevision(), RestconfModule.REVISION);
    }

    /**
     * Positive test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link RestconfModule#MODULES_CONTAINER_SCHEMA_NODE} when this node can be found.
     */
    @Test
    public void getRestconfSchemaNodeContainerModulesTest() {
        final DataSchemaNode dataSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(
                getTestingRestconfModule("ietf-restconf"),
                RestconfModule.MODULES_CONTAINER_SCHEMA_NODE);

        assertNotNull("Existing schema node " + RestconfModule.MODULES_CONTAINER_SCHEMA_NODE + "should be found",
                dataSchemaNode);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getLocalName(), RestconfModule.MODULES_CONTAINER_SCHEMA_NODE);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getNamespace().toString(), RestconfModule.NAMESPACE);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getFormattedRevision(), RestconfModule.REVISION);
    }

    /**
     * Positive test for getting <code>DataSchemaNode</code> from Restconf module for schema node name equals to
     * {@link MonitoringModule#STREAMS_CONTAINER_SCHEMA_NODE} when this node can be found.
     */
    @Test
    public void getRestconfSchemaNodeContainerStreamsTest() {
        final DataSchemaNode dataSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(
                getTestingRestconfModule("ietf-restconf"),
                MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE);

        assertNotNull("Existing schema node " + MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE + " should be found",
                dataSchemaNode);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getLocalName(), MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getNamespace().toString(), RestconfModule.NAMESPACE);
        assertEquals("Incorrect schema node was returned",
                dataSchemaNode.getQName().getFormattedRevision(), RestconfModule.REVISION);
    }

    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module when Restconf module is
     * <code>null</code>. Test is expected to fail catching <code>NullPointerException</code>.
     */
    @Test
    public void getRestconfSchemaNodeNullRestconfModuleNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        RestconfSchemaUtil.getRestconfSchemaNode(null, RestconfModule.RESTCONF_CONTAINER_SCHEMA_NODE);
    }

    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module when name of the schema node name is
     * <code>null</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getRestconfSchemaNodeNullSchemaNodeNameNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        RestconfSchemaUtil.getRestconfSchemaNode(getTestingRestconfModule("ietf-restconf"), null);
    }

    /**
     * Negative test for getting <code>DataSchemaNode</code> from Restconf module when name of the schema node name
     * references to not existing node. Test is expected to fail catching code>RestconfDocumentedException</code> and
     * checking expected error type, error tag and error status code.
     */
    @Test
    public void getRestconfSchemaNodeNotExistingSchemaNodeNameNegativeTest() {
        try {
            RestconfSchemaUtil.getRestconfSchemaNode(getTestingRestconfModule("ietf-restconf"), "not-existing-node");
            fail("Test should fail due to not-existing node name");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    RestconfError.ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Positive test trying to find <code>DataSchemaNode</code> of {@link RestconfModule#RESTCONF_GROUPING_SCHEMA_NODE}
     * in Restconf module groupings collection.
     */
    @Test
    public void findSchemaNodeInCollectionTest() {
        final SchemaNode schemaNode = RestconfSchemaUtil.findSchemaNodeInCollection(
                getTestingRestconfModule("ietf-restconf").getGroupings(),
                RestconfModule.RESTCONF_GROUPING_SCHEMA_NODE);

        assertNotNull("Restconf grouping schema node should be found", schemaNode);
        assertEquals("Incorrect grouping was returned",
                schemaNode.getQName().getLocalName(), RestconfModule.RESTCONF_GROUPING_SCHEMA_NODE);
        assertEquals("Incorrect grouping was returned",
                schemaNode.getQName().getNamespace().toString(), RestconfModule.NAMESPACE);
        assertEquals("Incorrect grouping was returned",
                schemaNode.getQName().getFormattedRevision(), RestconfModule.REVISION);
    }

    /**
     * Negative test trying to find <code>DataSchemaNode</code> of not existing groupings schema node name in Restconf
     * module grouping collection. Test is expected to fail catching <code>RestconfDocumentedException</code> and
     * checking for correct error type, error tag and error status code.
     */
    @Test
    public void findSchemaNodeInCollectionNegativeTest() {
        try {
            RestconfSchemaUtil.findSchemaNodeInCollection(
                    getTestingRestconfModule("ietf-restconf").getGroupings(), "not-existing-grouping");
            fail("Test should fail due to missing not-existing grouping in Restconf grouping collection");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    RestconfError.ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test trying to find <code>DataSchemaNode</code> of existing schema node name in <code>null</code>
     * collection. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void findSchemaNodeInCollectionNullCollectionNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        RestconfSchemaUtil.findSchemaNodeInCollection(null, RestconfModule.MODULES_CONTAINER_SCHEMA_NODE);
    }

    /**
     * Negative test trying to find <code>DataSchemaNode</code> for schema node name in empty collection. Test is
     * expected to fail with <code>RestconfDocumentedException</code>. Error type, error tag and error status code
     * are compared to expected values.
     */
    @Test
    public void findSchemaNodeInCollectionEmptyCollectionNegativeTest() {
        try {
            RestconfSchemaUtil.findSchemaNodeInCollection(
                    Sets.newHashSet(), RestconfModule.MODULES_CONTAINER_SCHEMA_NODE);
            fail("Test should fail due to empty schema nodes collection");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    RestconfError.ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test trying to find <code>DataSchemaNode</code> of <code>null</code> schema node name in Restconf module
     * groupings collection. Test is expected to fail with <code>RestconfDocumentedException</code>. Error type, error
     * tag and error status code are compared to expected values.
     */
    @Test
    public void findSchemaNodeInCollectionNullSchemaNodeName() {
        try {
            RestconfSchemaUtil.findSchemaNodeInCollection(
                    getTestingRestconfModule("ietf-restconf").getGroupings(), null);
            fail("Test should fail due to null schema node name");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    RestconfError.ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * There are multiple testing Restconf modules for different test cases. It is possible to distinguish them by
     * name or by namespace. This method is looking for Restconf test module by its name.
     * @param s Testing Restconf module name
     * @return Restconf module
     */
    private Module getTestingRestconfModule(final String s) {
        return this.schemaContext.findModuleByName(s, Draft18.RestconfModule.IETF_RESTCONF_QNAME.getRevision());
    }
}
