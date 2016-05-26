/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

/**
 * Unit tests for {@link RestConnectorProvider}
 */
public class RestConnectorProviderTest {
    // service under test
    private RestConnectorProvider connectorProvider;

    @Mock private Broker.ProviderSession mockSession;
    @Mock private SchemaService mockSchemaService;
    @Mock private DOMMountPointService mockMountPointService;
    @Mock private ListenerRegistration<SchemaContextListener> mockRegistration;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        this.connectorProvider = new RestConnectorProvider();
    }

    /**
     * Test of successful initialization of {@link RestConnectorProvider}.
     */
    @Test
    public void restConnectorProviderInitTest() {
        assertNotNull("Connector provider should be initialized and not null", this.connectorProvider);
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
        // prepare conditions
        when(this.mockSession.getService(SchemaService.class)).thenReturn(this.mockSchemaService);
        when(this.mockSession.getService(DOMMountPointService.class)).thenReturn(this.mockMountPointService);
        final DOMDataBroker mockDataBroker = Mockito.mock(DOMDataBroker.class);
        when(this.mockSession.getService(DOMDataBroker.class)).thenReturn(mockDataBroker);
        final DOMTransactionChain mockTransactionChain = Mockito.mock(DOMTransactionChain.class);
        when(mockDataBroker.createTransactionChain(Mockito.any())).thenReturn(mockTransactionChain);

        // test
        this.connectorProvider.onSessionInitiated(this.mockSession);

        // verify interactions
        verify(this.mockSession, times(1)).getService(SchemaService.class);
        verify(this.mockSession, times(1)).getService(DOMMountPointService.class);
        verify(this.mockSchemaService, times(1)).registerSchemaContextListener(Mockito.any(SchemaContextHandler.class));
    }

    /**
     * Test for successful registration with {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)}
     * without <code>DOMMountPointService</code>.
     * <p>
     * Condition 1: <code>Broker.ProviderSession</code> contains <code>SchemaService</code>
     * Condition 2: <code>Broker.ProviderSession</code> does not contain <code>DOMMountPointService</code>
     */
    @Test
    public void successfulRegistrationWithoutMountPointTest() {
        // prepare conditions
        when(this.mockSession.getService(SchemaService.class)).thenReturn(this.mockSchemaService);
        when(this.mockSession.getService(DOMMountPointService.class)).thenReturn(null);
        final DOMDataBroker mockDataBroker = Mockito.mock(DOMDataBroker.class);
        when(this.mockSession.getService(DOMDataBroker.class)).thenReturn(mockDataBroker);
        final DOMTransactionChain mockTransactionChain = Mockito.mock(DOMTransactionChain.class);
        when(mockDataBroker.createTransactionChain(Mockito.any())).thenReturn(mockTransactionChain);
        final DOMMountPointService mockDomMountPoint = Mockito.mock(DOMMountPointService.class);
        when(this.mockSession.getService(DOMMountPointService.class)).thenReturn(mockDomMountPoint);

        // test
        this.connectorProvider.onSessionInitiated(this.mockSession);

        // verify interactions
        verify(this.mockSession, times(1)).getService(SchemaService.class);
        verify(this.mockSession, times(1)).getService(DOMMountPointService.class);
        verify(this.mockSchemaService, times(1)).registerSchemaContextListener(Mockito.any(SchemaContextHandler.class));
    }

    /**
     * Negative test of registration with {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)} with
     * null input. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void nullSessionRegistrationNegativeTest() {
        this.thrown.expect(NullPointerException.class);
        this.connectorProvider.onSessionInitiated(null);
    }

    /**
     * Negative test of registration with {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)} when
     * <code>Broker.ProviderSession</code> does not contain <code>SchemaService</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void withoutSchemaServiceRegistrationNegativeTest() {
        // prepare conditions
        when(this.mockSession.getService(SchemaService.class)).thenReturn(null);

        // test
        this.thrown.expect(NullPointerException.class);
        this.connectorProvider.onSessionInitiated(this.mockSession);

        // verify interaction
        verify(this.mockSession, times(1)).getService(SchemaService.class);
    }

    /**
     * Test of closing <code>null</code> registration.
     */
    @Test
    public void closeNotOpenTest() throws Exception {
        this.connectorProvider.close();
    }

    /**
     * Test of creating and closing not <code>null</code> registration.
     */
    @Test
    public void closeOpenTest() throws Exception {
        // prepare conditions
        when(this.mockSession.getService(SchemaService.class)).thenReturn(this.mockSchemaService);
        when(this.mockSession.getService(DOMMountPointService.class)).thenReturn(this.mockMountPointService);
        when(this.mockSchemaService.registerSchemaContextListener(Mockito.any(SchemaContextHandler.class)))
                .thenReturn(this.mockRegistration);
        final DOMDataBroker mockDataBroker = Mockito.mock(DOMDataBroker.class);
        when(this.mockSession.getService(DOMDataBroker.class)).thenReturn(mockDataBroker);
        final DOMTransactionChain mockTransactionChain = Mockito.mock(DOMTransactionChain.class);
        when(mockDataBroker.createTransactionChain(Mockito.any())).thenReturn(mockTransactionChain);

        // register
        this.connectorProvider.onSessionInitiated(this.mockSession);

        // close registration
        this.connectorProvider.close();

        // verify interaction
        verify(this.mockRegistration, times(1)).close();
    }
}
