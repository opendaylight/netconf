/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesWrapperImpl;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

/**
 * Unit tests for {@link RestConnectorProvider}.
 */
public class RestConnectorProviderTest {
    // service under test
    private RestConnectorProvider connectorProvider;

    @Mock private SchemaService mockSchemaService;
    @Mock private DOMMountPointService mockMountPointService;
    @Mock private DOMDataBroker mockDataBroker;
    @Mock private DOMRpcService mockRpcService;
    @Mock private DOMNotificationService mockNotificationService;
    @Mock DOMTransactionChain mockTransactionChain;
    @Mock private ListenerRegistration<SchemaContextListener> mockRegistration;
    @Mock
    private DOMSchemaService domSchemaService;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        doReturn(mockTransactionChain).when(mockDataBroker).createTransactionChain(Mockito.any());
        doReturn(mockRegistration).when(mockSchemaService).registerSchemaContextListener(
                Mockito.any(SchemaContextHandler.class));

        this.connectorProvider = new RestConnectorProvider(mockDataBroker, mockSchemaService, mockRpcService,
                mockNotificationService, mockMountPointService, domSchemaService, ServicesWrapperImpl.getInstance());
    }

    /**
     * Test for successful start when all conditions are satisfied.
     */
    @Test
    public void successfulStartTest() {
        // test
        this.connectorProvider.start();

        // verify interactions
        verify(mockDataBroker).createTransactionChain(Mockito.any());
        verify(mockSchemaService).registerSchemaContextListener(Mockito.any(SchemaContextHandler.class));
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
        // start
        this.connectorProvider.start();

        // close
        this.connectorProvider.close();

        // verify interaction
        verify(this.mockRegistration).close();
        verify(mockTransactionChain).close();
    }
}
