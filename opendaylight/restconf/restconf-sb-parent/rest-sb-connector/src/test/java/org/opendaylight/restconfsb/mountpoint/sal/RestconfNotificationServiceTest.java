/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class RestconfNotificationServiceTest {

    @Mock
    private DOMNotification notification1;
    @Mock
    private DOMNotification notification2;
    @Mock
    private DOMNotificationListener listener1;
    @Mock
    private DOMNotificationListener listener2;
    private SchemaPath notification1Type;
    private SchemaPath notification2Type;

    private RestconfNotificationService service;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        notification1Type = SchemaPath.create(true, QName.create("ns", "2015-02-28", "notification1"));
        notification2Type = SchemaPath.create(true, QName.create("ns", "2015-02-28", "notification2"));
        service = new RestconfNotificationService();
        doReturn(notification1Type).when(notification1).getType();
        doReturn(notification2Type).when(notification2).getType();
        doNothing().when(listener1).onNotification(notification1);
        doNothing().when(listener2).onNotification(notification1);
        doNothing().when(listener2).onNotification(notification2);
    }

    @Test
    public void testOnNotification() throws Exception {
        service.registerNotificationListener(listener1, Collections.singleton(notification1Type));
        service.registerNotificationListener(listener2, notification1Type, notification2Type);
        service.onNotification(notification1);
        service.onNotification(notification2);
        verify(listener1).onNotification(notification1);
        verify(listener1, never()).onNotification(notification2);
        verify(listener2).onNotification(notification1);
        verify(listener2).onNotification(notification2);
    }

    @Test
    public void testCloseSubscription() throws Exception {
        final ListenerRegistration<DOMNotificationListener> subscription =
                service.registerNotificationListener(listener1, notification1Type);
        service.onNotification(notification1);
        subscription.close();
        service.onNotification(notification1);
        service.onNotification(notification1);
        verify(listener1, times(1)).onNotification(notification1);
    }
}