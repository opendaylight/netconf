/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

@ExtendWith(MockitoExtension.class)
class NetconfDeviceNotificationServiceTest {
    private static final Absolute PATH1 = Absolute.of(QName.create("namespace1", "path1"));
    private static final Absolute PATH2 = Absolute.of(QName.create("namespace2", "path2"));

    @Mock
    private DOMNotificationListener listener1;
    @Mock
    private DOMNotificationListener listener2;
    @Mock
    private DOMNotification notification1;
    @Mock
    private DOMNotification notification2;

    private final NetconfDeviceNotificationService service = new NetconfDeviceNotificationService();
    private ListenerRegistration<DOMNotificationListener> registration;

    @BeforeEach
    void beforeEach() throws Exception {
        service.registerNotificationListener(listener1, PATH1);
        registration = service.registerNotificationListener(listener2, PATH2);

        doReturn(PATH2).when(notification2).getType();
    }

    @Test
    void testPublishNotification() throws Exception {
        doReturn(PATH1).when(notification1).getType();

        service.publishNotification(notification1);
        verify(listener1).onNotification(notification1);
        verify(listener2, never()).onNotification(notification1);

        service.publishNotification(notification2);
        verify(listener2).onNotification(notification2);
        verify(listener1, never()).onNotification(notification2);
    }

    @Test
    void testCloseRegistration() throws Exception {
        service.publishNotification(notification2);
        assertEquals(listener2, registration.getInstance());
        registration.close();
        service.publishNotification(notification2);
        verify(listener2, times(1)).onNotification(notification2);
    }
}