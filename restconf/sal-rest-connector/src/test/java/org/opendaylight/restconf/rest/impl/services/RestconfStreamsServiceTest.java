/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest.impl.services;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link RestconfStreamsServiceImpl}
 */
public class RestconfStreamsServiceTest {
    @Mock
    private SchemaContextHandler contextHandler;

    private SchemaContext schemaContext;

    // service under test
    private RestconfStreamsService streamsService;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        streamsService = new RestconfStreamsServiceImpl(contextHandler);

        Mockito.when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
    }

    /**
     * Positive test to get all available streams supported by the server.
     */
    @Ignore
    @Test
    public void getAvailableStreamsTest() {}

    // NEW TEST PLAN
    /**
     * Try to get all available streams supported by the server when current <code>SchemaContext</code> is
     * <code>null</code> catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void getAvailableStreamsNullSchemaContextNegativeTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module is missing in
     * <code>SchemaContext</code> catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void getAvailableStreamsMissingRestconfModuleNegativeTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module does not contain list stream
     * catching <code>RestconfDocumentedException</code>. Error type, error tag and error status code are validated
     * against expected values.
     */
    @Ignore
    @Test
    public void getAvailableStreamsMissingStreamListNegativeTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module does not contain container streams
     * catching <code>RestconfDocumentedException</code>. Error type, error tag and error status code are validated
     * against expected values.
     */
    @Ignore
    @Test
    public void getAvailableStreamsMissingStreamsContainerNegativeTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module contains node stream but it is
     * not of type list. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Ignore
    @Test
    public void getAvailableStreamsExpectedStreamListNegativeTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module contains node streams but it is
     * not of type container. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Ignore
    @Test
    public void getAvailableStreamsExpectedStreamsContainerNegativeTest() {}
}
