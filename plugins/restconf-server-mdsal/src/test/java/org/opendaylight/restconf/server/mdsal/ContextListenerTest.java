/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
public class ContextListenerTest {
    private static final EffectiveModelContext CONTEXT =
        YangParserTestUtils.parseYangResources(ContextListenerTest.class ,"/notification-test@2025-03-03.yang");
    private static final EffectiveModelContext NEW_CONTEXT =
        YangParserTestUtils.parseYangResources(ContextListenerTest.class ,
            "/notification-test@2025-03-03.yang", "/toaster@2009-11-20.yang");
    @Mock
    private DOMNotificationService notificationService;
    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private RestconfStream.LocationProvider locationProvider;
    @Mock
    private RestconfStream.Registry streamRegistry;
    @Mock
    private RestconfStream.Sink<DOMNotification> sink;

    @Captor
    private ArgumentCaptor<DefaultNotificationSource> defaultNotificationSourceCaptor;
    @Captor
    private ArgumentCaptor<Consumer<EffectiveModelContext>> consumerArgumentCaptor;
    @Captor
    private ArgumentCaptor<Collection<SchemaNodeIdentifier.Absolute>> listCaptor;

    @Test
    void deleteSubscriptionTest() {
        final var mdsalRestconfStreamRegistry = new MdsalRestconfStreamRegistry(dataBroker, locationProvider);
        mdsalRestconfStreamRegistry.lookupStream("NETCONF");

        final var inOrderRegistry = inOrder(streamRegistry);
        final var inOrderService = inOrder(notificationService);

        doReturn(CONTEXT).when(schemaService).getGlobalContext();
        final var contextListener = new ContextListener(notificationService, schemaService, streamRegistry);
        inOrderRegistry.verify(streamRegistry).start(defaultNotificationSourceCaptor.capture());
        verify(schemaService).registerSchemaContextListener(consumerArgumentCaptor.capture());

        defaultNotificationSourceCaptor.getValue().start(sink);
        inOrderService.verify(notificationService).registerNotificationListener(any(), listCaptor.capture());
        assertEquals(1, listCaptor.getValue().size());


        consumerArgumentCaptor.getValue().accept(NEW_CONTEXT);
        inOrderRegistry.verify(streamRegistry).start(defaultNotificationSourceCaptor.capture());
        defaultNotificationSourceCaptor.getValue().start(sink);
        inOrderService.verify(notificationService).registerNotificationListener(any(), listCaptor.capture());
        assertEquals(3, listCaptor.getValue().size());
    }
}
