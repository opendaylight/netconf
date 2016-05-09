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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.model.SchemaService;

/**
 * Unit tests for {@link RestConnectorProvider}
 */
public class RestConnectorProviderTest {
    // service under test
    private RestConnectorProvider connectorProvider;

    @Mock private Broker.ProviderSession session;
    @Mock private SchemaService schemaService;
    @Mock private DOMMountPointService mountPointService;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        connectorProvider = new RestConnectorProvider();
        //schemaService = GlobalBundleScanningSchemaServiceImpl.createInstance(mock(BundleContext.class));
    }

    /**
     * Test of successful initialization of {@link RestConnectorProvider}.
     */
    @Test
    public void restConnectorProviderInitTest() {
        assertNotNull("Connector provider should be initialized and not null", connectorProvider);
    }

    /**
     * Test for successful registration with {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)}
     * when all conditions are satisfied.
     * <p>
     * Condition 1: <code>Broker.ProviderSession</code> contains <code>SchemaService</code>
     * Condition 2: <code>Broker.ProviderSession</code> contains <code>DOMMountPointService</code>
     */
    @Test
    public void successfulRegistrationTest() {
        when(session.getService(SchemaService.class)).thenReturn(schemaService);
        when(session.getService(DOMMountPointService.class)).thenReturn(mountPointService);

        connectorProvider.onSessionInitiated(session);
    }

    /**
     * Test for successful registration with {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)}
     * when all conditions are satisfied.
     * <p>
     * Condition 1: <code>Broker.ProviderSession</code> contains <code>SchemaService</code>
     * Condition 2: <code>Broker.ProviderSession</code> does not contain <code>DOMMountPointService</code>
     */
    @Test
    public void successfulRegistrationWithoutMountPointTest() {
        when(session.getService(SchemaService.class)).thenReturn(schemaService);
        when(session.getService(DOMMountPointService.class)).thenReturn(null);

        connectorProvider.onSessionInitiated(session);
    }

    /**
     * Test method {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)} with null input. Test is
     * expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void nullSessionRegistrationNegativeTest() {
        thrown.expect(NullPointerException.class);
        connectorProvider.onSessionInitiated(null);
    }

    /**
     * Negative test of registration with {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)} when
     * <code>Broker.ProviderSession</code> does not contain <code>SchemaService</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void withoutSchemaServiceRegistrationNegativeTest() {
        when(session.getService(SchemaService.class)).thenReturn(null);

        thrown.expect(NullPointerException.class);
        connectorProvider.onSessionInitiated(session);
    }
}
