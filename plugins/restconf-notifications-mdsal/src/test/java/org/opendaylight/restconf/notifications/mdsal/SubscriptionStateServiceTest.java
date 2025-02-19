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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.BASE_QNAME;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.COMPLETED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.MODIFIED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.NETCONF_NOTIFICATION;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.REASON;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.RESUMED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.SUSPENDED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.TERMINATED;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.InsufficientResources;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SuspensionTimeout;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

@ExtendWith(MockitoExtension.class)
class SubscriptionStateServiceTest {
    private static final Instant EVENT_TIME =
        OffsetDateTime.of(LocalDateTime.of(2024, Month.OCTOBER, 30, 12, 34, 56), ZoneOffset.UTC).toInstant();
    private static final Uint32 ID = Uint32.valueOf(2147483648L);
    private static final String URI = "http://example.com";
    private static final String FILTER = "example-filter";
    private static final String STREAM_NAME = "NETCONF";

    @Mock
    private DOMNotificationPublishService mockPublishService;
    @Captor
    private ArgumentCaptor<DOMNotification> captor;

    private SubscriptionStateService notifications;

    @BeforeEach
    void setUp() {
        notifications = new SubscriptionStateService(mockPublishService);
    }

    enum SubscriptionType {
        MODIFIED, COMPLETED, RESUMED, TERMINATED, SUSPENDED
    }

    @ParameterizedTest
    @EnumSource(SubscriptionType.class)
    void testSubscriptionEvents(final SubscriptionType type) throws InterruptedException {
        when(mockPublishService.putNotification(any())).thenReturn(FluentFutures.immediateNullFluentFuture());

        final QName eventQName = switch (type) {
            case MODIFIED -> {
                notifications.subscriptionModified(EVENT_TIME, ID, URI, STREAM_NAME, FILTER);
                yield QName.create(NETCONF_NOTIFICATION, MODIFIED);
            }
            case COMPLETED -> {
                notifications.subscriptionCompleted(EVENT_TIME, ID);
                yield QName.create(NETCONF_NOTIFICATION, COMPLETED);
            }
            case RESUMED -> {
                notifications.subscriptionResumed(EVENT_TIME, ID);
                yield QName.create(NETCONF_NOTIFICATION, RESUMED);
            }
            case TERMINATED -> {
                notifications.subscriptionTerminated(EVENT_TIME, ID, SuspensionTimeout.QNAME);
                yield QName.create(NETCONF_NOTIFICATION, TERMINATED);
            }
            case SUSPENDED -> {
                notifications.subscriptionSuspended(EVENT_TIME, ID, InsufficientResources.QNAME);
                yield QName.create(NETCONF_NOTIFICATION, SUSPENDED);
            }
        };

        verify(mockPublishService).putNotification(captor.capture());
        final var notification = captor.getValue();
        assertNotNull(notification);
        assertEquals("2024-10-30T12:34:56Z", notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.EVENT_TIME)).body());

        final var eventNode = (ContainerNode) notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(eventQName));

        assertNotNull(eventNode);
        assertEquals(ID, eventNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.ID)).body());

        switch (type) {
            case null -> throw new NullPointerException();
            case MODIFIED -> {
                assertEquals(URI, eventNode.getChildByArg(
                    new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.URI)).body());
                assertEquals(FILTER, eventNode.getChildByArg(
                    new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.FILTER)).body());
                final var streamNode = (ContainerNode) eventNode.getChildByArg(
                    new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE_QNAME, "stream")));
                assertNotNull(streamNode);
                assertEquals(STREAM_NAME, streamNode.getChildByArg(new YangInstanceIdentifier.NodeIdentifier(
                    QName.create(BASE_QNAME, "ietf-netconf-subscribed-notifications"))).body());
            }
            case SUSPENDED ->
                assertEquals(InsufficientResources.QNAME, eventNode.getChildByArg(
                    new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE_QNAME, REASON))).body());
            case TERMINATED ->
                assertEquals(SuspensionTimeout.QNAME, eventNode.getChildByArg(
                    new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE_QNAME, REASON))).body());
            case COMPLETED, RESUMED -> {
                // Nothing else
            }
        }
    }
}
