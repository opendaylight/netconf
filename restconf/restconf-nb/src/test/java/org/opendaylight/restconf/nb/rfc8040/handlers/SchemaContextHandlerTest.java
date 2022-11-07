/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.FileNotFoundException;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Capabilities;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Tests for handling {@link SchemaContext}.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SchemaContextHandlerTest {

    private static final String PATH_FOR_ACTUAL_SCHEMA_CONTEXT = "/modules";
    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/modules/modules-behind-mount-point";

    private static EffectiveModelContext SCHEMA_CONTEXT;

    private SchemaContextHandler schemaContextHandler;

    @Mock
    private DOMSchemaService mockDOMSchemaService;
    @Mock
    private ListenerRegistration<?> mockListenerReg;

    @BeforeClass
    public static void beforeClass() throws FileNotFoundException {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangFiles(
            TestRestconfUtils.loadFiles(PATH_FOR_ACTUAL_SCHEMA_CONTEXT));
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setup() throws Exception {
        final DOMDataBroker dataBroker = mock(DOMDataBroker.class);
        final DOMDataTreeWriteTransaction wTx = mock(DOMDataTreeWriteTransaction.class);
        doReturn(wTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(wTx).commit();

        doReturn(mockListenerReg).when(mockDOMSchemaService).registerSchemaContextListener(any());

        schemaContextHandler = new SchemaContextHandler(dataBroker, mockDOMSchemaService);
        verify(mockDOMSchemaService).registerSchemaContextListener(schemaContextHandler);

        schemaContextHandler.onModelContextUpdated(SCHEMA_CONTEXT);
    }

    /**
     * Testing init and close.
     */
    @Test
    public void testInitAndClose() {
        schemaContextHandler.close();
        verify(mockListenerReg).close();
    }

    /**
     * Test getting actual {@link SchemaContext}.
     *
     * <p>
     * Get <code>SchemaContext</code> from <code>SchemaContextHandler</code> and compare it to actual
     * <code>SchemaContext</code>.
     */
    @Test
    public void getSchemaContextTest() {
        assertEquals("SchemaContextHandler should has reference to actual SchemaContext",
                SCHEMA_CONTEXT, schemaContextHandler.get());
    }

    /**
     * Test updating of {@link SchemaContext}.
     *
     * <p>
     * Create new <code>SchemaContext</code>, set it to <code>SchemaContextHandler</code> and check if
     * <code>SchemaContextHandler</code> reference to new <code>SchemaContext</code> instead of old one.
     */
    @Test
    public void onGlobalContextUpdateTest() throws Exception {
        // create new SchemaContext and update SchemaContextHandler
        final EffectiveModelContext newSchemaContext =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));
        schemaContextHandler.onModelContextUpdated(newSchemaContext);

        assertNotEquals("SchemaContextHandler should not has reference to old SchemaContext",
                SCHEMA_CONTEXT, schemaContextHandler.get());
        assertEquals("SchemaContextHandler should has reference to new SchemaContext",
                newSchemaContext, schemaContextHandler.get());
    }

    @Test
    public void restconfStateCapabilitesTest() {
        final ContainerNode normNode = SchemaContextHandler.mapCapabilites();

        @SuppressWarnings("unchecked")
        final LeafSetNode<String> capability = (LeafSetNode<String>) normNode.body().stream()
            // Find 'capabilities' container
            .filter(child -> Capabilities.QNAME.equals(child.getIdentifier().getNodeType()))
            .findFirst()
            .map(ContainerNode.class::cast)
            .orElseThrow()
            // Find 'capability' leaf-list
            .body().stream()
            .filter(child -> SchemaContextHandler.CAPABILITY_QNAME.equals(child.getIdentifier().getNodeType()))
            .findFirst()
            .orElseThrow();

        assertThat(
            capability.body().stream().map(entry -> ((LeafSetEntryNode<?>) entry).body()).collect(Collectors.toList()),
            containsInAnyOrder(
                equalTo("urn:ietf:params:restconf:capability:depth:1.0"),
                equalTo("urn:ietf:params:restconf:capability:fields:1.0"),
                equalTo("urn:ietf:params:restconf:capability:filter:1.0"),
                equalTo("urn:ietf:params:restconf:capability:replay:1.0"),
                equalTo("urn:ietf:params:restconf:capability:with-defaults:1.0"),
                equalTo("urn:opendaylight:params:restconf:capability:pretty-print:1.0"),
                equalTo("urn:opendaylight:params:restconf:capability:leaf-nodes-only:1.0"),
                equalTo("urn:opendaylight:params:restconf:capability:changed-leaf-nodes-only:1.0"),
                equalTo("urn:opendaylight:params:restconf:capability:skip-notification-data:1.0")));
    }

}
