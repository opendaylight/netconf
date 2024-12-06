/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.BASE_QNAME;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.COMPLETED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.MODIFIED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.NETCONF_NOTIFICATION;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.RESUMED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.SUSPENDED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.TERMINATED;

import com.google.common.util.concurrent.ListenableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

@ExtendWith(MockitoExtension.class)
class SubscriptionStateServiceTest {

    private static final String EVENT_TIME = "2024-10-30T12:34:56Z";
    private static final Long ID = 2147483648L;
    private static final String URI = "http://example.com";
    private static final String FILTER = "example-filter";
    private static final String STREAM_NAME = "NETCONF";
    private static final String ERROR_ID = "example-error";

    private SubscriptionStateService notifications;

    @Mock
    private DOMNotificationPublishService mockPublishService;

    @Captor
    private ArgumentCaptor<DOMNotification> captor;

    @BeforeEach
    void setUp() {
        notifications = new SubscriptionStateService(mockPublishService);
    }

    @Test
    void testSubscriptionModified() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionModified(EVENT_TIME, ID, URI, STREAM_NAME, FILTER);

        verify(mockPublishService).putNotification(captor.capture());
        final var notification = captor.getValue();

        assertNotNull(notification);
        assertEquals(EVENT_TIME, notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.EVENT_TIME)).body());

        final var modifiedNode = (ContainerNode) notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(QName.create(NETCONF_NOTIFICATION, MODIFIED)));

        assertNotNull(modifiedNode);
        assertEquals(ID, modifiedNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.ID)).body());
        assertEquals(URI, modifiedNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.URI)).body());
        assertEquals(FILTER, modifiedNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.FILTER)).body());

        final var streamNode = (ContainerNode) modifiedNode
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE_QNAME, "stream")));

        assertNotNull(streamNode);
        assertEquals(STREAM_NAME, streamNode.getChildByArg(new YangInstanceIdentifier.NodeIdentifier(
            QName.create(BASE_QNAME, "ietf-netconf-subscribed-notifications"))).body());
    }

    @Test
    void testSubscriptionCompleted() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionCompleted(EVENT_TIME, ID);

        verify(mockPublishService).putNotification(captor.capture());
        final var notification = captor.getValue();

        assertNotNull(notification);
        assertEquals(EVENT_TIME, notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.EVENT_TIME)).body());

        final var completedNode = (ContainerNode) notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(QName.create(NETCONF_NOTIFICATION, COMPLETED)));

        assertNotNull(completedNode);
        assertEquals(ID, completedNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.ID)).body());
    }

    @Test
    void testSubscriptionResumed() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionResumed(EVENT_TIME, ID);

        verify(mockPublishService).putNotification(captor.capture());
        final var notification = captor.getValue();

        assertNotNull(notification);
        assertEquals(EVENT_TIME, notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.EVENT_TIME)).body());

        final var resumedNode = (ContainerNode) notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(QName.create(NETCONF_NOTIFICATION, RESUMED)));

        assertNotNull(resumedNode);
        assertEquals(ID, resumedNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.ID)).body());
    }

    @Test
    void testSubscriptionTerminated() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionTerminated(EVENT_TIME, ID, ERROR_ID);

        verify(mockPublishService).putNotification(captor.capture());
        final var notification = captor.getValue();

        assertNotNull(notification);
        assertEquals(EVENT_TIME, notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.EVENT_TIME)).body());

        final var terminatedNode = (ContainerNode) notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(QName.create(NETCONF_NOTIFICATION, TERMINATED)));

        assertNotNull(terminatedNode);
        assertEquals(ID, terminatedNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.ID)).body());
        assertEquals(ERROR_ID, terminatedNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE_QNAME, "error-id"))).body());
    }

    @Test
    void testSubscriptionSuspended() throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(mock(ListenableFuture.class));

        notifications.subscriptionSuspended(EVENT_TIME, ID, ERROR_ID);

        verify(mockPublishService).putNotification(captor.capture());
        final var notification = captor.getValue();

        assertNotNull(notification);
        assertEquals(EVENT_TIME, notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.EVENT_TIME)).body());

        final var suspendedNode = (ContainerNode) notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(QName.create(NETCONF_NOTIFICATION, SUSPENDED)));

        assertNotNull(suspendedNode);
        assertEquals(ID, suspendedNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.ID)).body());
        assertEquals(ERROR_ID, suspendedNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE_QNAME, "error-id"))).body());
    }
}
