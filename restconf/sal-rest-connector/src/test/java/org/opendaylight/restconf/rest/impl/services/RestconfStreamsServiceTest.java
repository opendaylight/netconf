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
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.restconf.rest.impl.services.RestconfStreamsServiceImpl;
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
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        streamsService = new RestconfStreamsServiceImpl(contextHandler);

        Mockito.when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
    }

    @Test
    public void getAvailableStreamsTest() throws Exception {
        final NormalizedNodeContext streams = streamsService.getAvailableStreams(null);
        assertNotNull(streams);

        assertEquals("streams", streams.getData().getNodeType().getLocalName());
        final QNameModule restconfModule = streams.getData().getNodeType().getModule();
        assertNotNull(restconfModule);

        assertEquals("2013-10-19", restconfModule.getFormattedRevision());
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf", restconfModule.getNamespace().toString());
    }
}
