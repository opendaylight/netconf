/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest.impl.services;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class RestconfStreamsServiceTest {
    @Mock
    private SchemaContextHandler contextHandler;

    SchemaContext schemaContext;
    RestconfStreamsService streamsService;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        streamsService = new RestconfStreamsServiceImpl(contextHandler);

        Mockito.when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
    }

    /**
     * Get all streams supported by the server and cehck their count.
     */
    @Test
    public void getAvailableStreamsTest() {
        final NormalizedNodeContext streams = streamsService.getAvailableStreams(null);
        assertNotNull(streams);

        assertEquals("Looking for streams container", Draft11.MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE,
                streams.getData().getNodeType().getLocalName());

        final QNameModule restconfModule = streams.getData().getNodeType().getModule();
        assertNotNull("Restconf module should be found", restconfModule);

        assertEquals("Expected correct Restconf module revision",
                Draft11.RestconfModule.REVISION, restconfModule.getFormattedRevision());
        assertEquals("Expected correct Restconf module namespace",
                Draft11.RestconfModule.NAMESPACE, restconfModule.getNamespace().toString());
    }

    // NEW TEST PLAN
    /**
     * Try to get all available streams supported by the server when current <code>SchemaContext</code> is
     * <code>null</code> catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void nullSchemaContextTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module is missing in
     * <code>SchemaContext</code> catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void missingRestconfModuleTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module does not contain list stream
     * catching <code>RestconfDocumentedException</code>.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void missingStreamListTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module does not contain container streams
     * catching <code>RestconfDocumentedException</code>.
     */
    @Ignore
    @Test(expected = RestconfDocumentedException.class)
    public void missingStreamsContainerTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module contains node stream but it is
     * not a list.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void expectedStreamListTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module contains node streams but it is
     * not a container.
     */
    @Ignore
    @Test(expected = IllegalStateException.class)
    public void expectedStreamsContainerTest() {}
}
