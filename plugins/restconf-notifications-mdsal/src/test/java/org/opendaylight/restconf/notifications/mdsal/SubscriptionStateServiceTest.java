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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminatedReason;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SuspensionTimeout;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@ExtendWith(MockitoExtension.class)
class SubscriptionStateServiceTest {
    private static final DateAndTime EVENT_TIME = DateAndTime.getDefaultInstance("2024-10-30T12:34:56Z");
//    private static final Instant EVENT_TIME =
//        OffsetDateTime.of(LocalDateTime.of(2024, Month.OCTOBER, 30, 12, 34, 56), ZoneOffset.UTC).toInstant();
    private static final Uint32 ID = Uint32.valueOf(2147483648L);
    private static final String URI = "http://example.com";
    private static final String FILTER = "example-filter";
    private static final String STREAM_NAME = "NETCONF";
    private static final String ENCODING = "encode-json";
    private static final QName ERROR_REASON = SubscriptionTerminatedReason.QNAME;

    private SubscriptionStateService notifications;

    @Mock
    private DOMNotificationPublishService mockPublishService;
    @Captor
    private ArgumentCaptor<DOMNotification> captor;

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
                notifications.subscriptionModified(ID, STREAM_NAME, ENCODING, FILTER, EVENT_TIME);
                yield SubscriptionModified.QNAME;
            }
            case COMPLETED -> {
                notifications.subscriptionCompleted(ID);
                yield SubscriptionCompleted.QNAME;
            }
            case RESUMED -> {
                notifications.subscriptionResumed(ID);
                yield SubscriptionResumed.QNAME;
            }
            case TERMINATED -> {
                notifications.subscriptionTerminated(ID, SuspensionTimeout.QNAME);
                yield SubscriptionTerminated.QNAME;
            }
            case SUSPENDED -> {
                notifications.subscriptionSuspended(ID, InsufficientResources.QNAME);
                yield SubscriptionSuspended.QNAME;
            }
        };

        verify(mockPublishService).putNotification(captor.capture());
        final var notification = captor.getValue().getBody();
        assertNotNull(notification);
        assertEquals(ID, notification.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(QName.create(eventQName, "id"))).body());

        if (SubscriptionType.MODIFIED == type) {
            assertEquals(ENCODING, notification.getChildByArg(
                new YangInstanceIdentifier.NodeIdentifier(QName.create(eventQName, "encoding"))).body());
            assertEquals(FILTER, notification.getChildByArg(
                new YangInstanceIdentifier.NodeIdentifier(QName.create(eventQName, "stream-subtree-filter"))).body());
            assertEquals(STREAM_NAME, notification.getChildByArg(
                new YangInstanceIdentifier.NodeIdentifier(QName.create(eventQName, "stream"))).body());
            assertEquals(EVENT_TIME, notification.getChildByArg(
                new YangInstanceIdentifier.NodeIdentifier(QName.create(eventQName, "stop-time"))).body());
        }

        if (SubscriptionType.TERMINATED == type || SubscriptionType.SUSPENDED == type) {
            assertEquals(ERROR_REASON, notification.getChildByArg(
                new YangInstanceIdentifier.NodeIdentifier(QName.create(eventQName, "reason"))).body());
        }
    }
}
