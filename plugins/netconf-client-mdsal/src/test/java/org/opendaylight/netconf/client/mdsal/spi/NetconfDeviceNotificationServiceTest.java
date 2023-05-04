/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceNotificationServiceTest {
    @Mock
    private DOMNotificationListener listener1;
    @Mock
    private DOMNotificationListener listener2;
    @Mock
    private DOMNotification notification1;
    @Mock
    private DOMNotification notification2;

    private NetconfDeviceNotificationService service;
    private ListenerRegistration<DOMNotificationListener> registration;


    @Before
    public void setUp() throws Exception {
        final Absolute path1 = Absolute.of(QName.create("namespace1", "path1"));
        final Absolute path2 = Absolute.of(QName.create("namespace2", "path2"));
        service = new NetconfDeviceNotificationService();
        service.registerNotificationListener(listener1, path1);
        registration = service.registerNotificationListener(listener2, path2);

        doReturn(path1).when(notification1).getType();
        doReturn(path2).when(notification2).getType();
    }

    @Test
    public void testPublishNotification() throws Exception {

        service.publishNotification(notification1);
        verify(listener1).onNotification(notification1);
        verify(listener2, never()).onNotification(notification1);

        service.publishNotification(notification2);
        verify(listener2).onNotification(notification2);
        verify(listener1, never()).onNotification(notification2);
    }

    @Test
    public void testCloseRegistration() throws Exception {
        service.publishNotification(notification2);
        Assert.assertEquals(listener2, registration.getInstance());
        registration.close();
        service.publishNotification(notification2);
        verify(listener2, times(1)).onNotification(notification2);
    }
}