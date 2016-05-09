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

/**
 * Unit tests for <code>RestConnectorProvider</code>
 */
public class RestConnectorProviderTest {
    private RestConnectorProvider restConnectorProvider;

    @Mock private Broker.ProviderSession session;
    @Mock private SchemaService schemaService;

    @Before
    public void setup() {
        restConnectorProvider = new RestConnectorProvider();

        MockitoAnnotations.initMocks(this);
        when(session.getService(SchemaService.class)).thenReturn(schemaService);
    }

    /**
     * Test for successful initialization of <code>RestConnectorProvider</code>
     */
    @Test
    public void RestConnectorProviderInit() {
        assertNotNull("RestConnectorProvider should be initialized and not null ", restConnectorProvider);
    }

    /**
     * Test null session as an method input. Test should fail.
     */
    @Test(expected = NullPointerException.class)
    public void nullSessionInit() {
        restConnectorProvider.onSessionInitiated(null);
    }

    /**
     * Test for successful listener registration.
     *
     * This test is ignored because it is now not possible to mock <code>BundleContext</code>
     * which is getting from static FrameworkUtil method without adding more project dependencies.
     *
     * After migrating to Jersey 2.x it will be possible to mock <code>BundleContext</code> and implement this test.
     */
    @Ignore
    @Test
    public void successfulSessionInit() {}

    @Ignore
    @Test(expected = NullPointerException.class)
    /**
     * Negative test when current session does not contain SchemaService. Test should fail with
     * <code>NullPointerException</code>.
     *
     * This test is ignored because it is now not possible to mock <code>BundleContext</code>
     * which is getting from static FrameworkUtil method without adding more project dependencies.
     *
     * After migrating to Jersey 2.x it will be possible to mock <code>BundleContext</code> and implement this test.
     */
    public void sessionWithoutSchemaServiceTest() {}

    @Ignore
    @Test(expected = NullPointerException.class)
    /**
     * Negative test when BundleContext does not contain RestconfApplication. Test should fail with
     * <code>NullPointerException</code>.
     *
     * This test is ignored because it is now not possible to mock <code>BundleContext</code>
     * which is getting from static FrameworkUtil method without adding more project dependencies.
     *
     * After migrating to Jersey 2.x it will be possible to mock <code>BundleContext</code> and implement this test.
     */
    public void sessionWithoutRestconfApplicationTest() {}

    @Ignore
    @Test
    public void closeRegistarationTest() {}

}
