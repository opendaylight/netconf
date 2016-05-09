/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.model.SchemaService;

public class RestConnectorProviderTest {

    private RestConnectorProvider restConnectorProvider;

    @Mock
    private Broker.ProviderSession session;

    @Mock
    private SchemaService schemaService;

    @Before
    public void setup() {
        restConnectorProvider = new RestConnectorProvider();

        MockitoAnnotations.initMocks(this);
        when(session.getService(SchemaService.class)).thenReturn(schemaService);
    }

    @Test
    public void RestConnectorProviderInit() {
        assertNotNull(restConnectorProvider);
    }

    @Test
    public void successfulSessionInit() {
        restConnectorProvider.onSessionInitiated(session);
    }

    @Ignore
    @Test(expected = NullPointerException.class)
    /**
     * Negative test when current session does not contain SchemaService
     */
    public void sessionWithoutSchemaServiceTest() {}

    @Ignore
    @Test(expected = NullPointerException.class)
    /**
     * Negative test when Restconf application is not in Bundle context
     */
    public void sessionWithoutRestconfApplicationTest() {}

    @Ignore
    @Test
    public void closeRegistarationTest() {}

}
