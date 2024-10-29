/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;

@ExtendWith(MockitoExtension.class)
class StateChangeNotificationsTest {

    private static final String EVENT_TIME = "2024-10-30T12:34:56Z";
    private static final Long ID = 123L;
    private static final String URI = "http://example.com";
    private static final String FILTER = "some-filter";
    private static final String ERROR_ID = "error-123";
    private StateChangeNotifications notifications;

    @Mock
    private DOMNotificationPublishService mockPublishService;

    @BeforeEach
    void setUp() {
        notifications = new StateChangeNotifications(mockPublishService);
    }

    @Test
    void testSubscriptionModified() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionModified(EVENT_TIME, ID, URI, FILTER);

        ArgumentCaptor<DOMNotification> captor = ArgumentCaptor.forClass(DOMNotification.class);
        verify(mockPublishService).putNotification(captor.capture());
        DOMNotification notification = captor.getValue();

    }

    @Test
    void testSubscriptionCompleted() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionCompleted(EVENT_TIME, ID);

        ArgumentCaptor<DOMNotification> captor = ArgumentCaptor.forClass(DOMNotification.class);
        verify(mockPublishService).putNotification(captor.capture());
        DOMNotification notification = captor.getValue();

    }

    @Test
    void testSubscriptionResumed() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionResumed(EVENT_TIME, ID);

        ArgumentCaptor<DOMNotification> captor = ArgumentCaptor.forClass(DOMNotification.class);
        verify(mockPublishService).putNotification(captor.capture());
        DOMNotification notification = captor.getValue();

    }

    @Test
    void testSubscriptionTerminated() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionTerminated(EVENT_TIME, ID, ERROR_ID);

        ArgumentCaptor<DOMNotification> captor = ArgumentCaptor.forClass(DOMNotification.class);
        verify(mockPublishService).putNotification(captor.capture());
        DOMNotification notification = captor.getValue();

    }

    @Test
    void testSubscriptionSuspended() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionSuspended(EVENT_TIME, ID, ERROR_ID);

        ArgumentCaptor<DOMNotification> captor = ArgumentCaptor.forClass(DOMNotification.class);
        verify(mockPublishService).putNotification(captor.capture());
        DOMNotification notification = captor.getValue();

    }

}
