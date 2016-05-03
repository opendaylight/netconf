/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest.impl.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@RunWith(MockitoJUnitRunner.class)
public class RestconfStreamsServiceTest {
    @Mock
    private SchemaContextHandler contextHandler;

    SchemaContext schemaContext;
    RestconfStreamsService streamsService;

    @Before
    public void setup() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        streamsService = new RestconfStreamsServiceImpl(contextHandler);

        Mockito.when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
    }

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
}
