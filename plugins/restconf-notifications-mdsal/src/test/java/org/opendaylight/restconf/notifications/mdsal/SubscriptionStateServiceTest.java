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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminatedReason;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;

@ExtendWith(MockitoExtension.class)
class SubscriptionStateServiceTest {
    private static final Instant EVENT_TIME =
        OffsetDateTime.of(LocalDateTime.of(2024, Month.OCTOBER, 30, 12, 34, 56), ZoneOffset.UTC).toInstant();
    private static final Uint32 ID = Uint32.valueOf(2147483648L);
    private static final String STREAM_NAME = "NETCONF";
    private static final String URI = "http://example.com";
    private static final QName ENCODING = EncodeXml$I.QNAME;
    private static final QName ERROR_REASON = SubscriptionTerminatedReason.QNAME;
    private static final NodeIdentifier URI_NODEID = NodeIdentifier.create(
        org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117
            .YangModuleInfoImpl.qnameOf("uri"));

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
                notifications.subscriptionModified(EVENT_TIME, ID, STREAM_NAME, ENCODING, null, URI);
                yield SubscriptionModified.QNAME;
            }
            case COMPLETED -> {
                notifications.subscriptionCompleted(EVENT_TIME, ID);
                yield SubscriptionCompleted.QNAME;
            }
            case RESUMED -> {
                notifications.subscriptionResumed(EVENT_TIME, ID);
                yield SubscriptionResumed.QNAME;
            }
            case TERMINATED -> {
                notifications.subscriptionTerminated(EVENT_TIME, ID, ERROR_REASON);
                yield SubscriptionTerminated.QNAME;
            }
            case SUSPENDED -> {
                notifications.subscriptionSuspended(EVENT_TIME, ID, ERROR_REASON);
                yield SubscriptionSuspended.QNAME;
            }
        };

        verify(mockPublishService).putNotification(captor.capture());
        final var notification = captor.getValue().getBody();
        assertNotNull(notification);
        assertEquals(ID, notification.getChildByArg(
            new NodeIdentifier(QName.create(eventQName, "id"))).body());

        switch (type) {
            case null -> throw new NullPointerException();
            case MODIFIED -> {
                assertEquals(ENCODING, notification.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "encoding"))).body());
                assertEquals(STREAM_NAME, notification.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "stream"))).body());
                assertEquals(URI, notification.getChildByArg(URI_NODEID).body());
            }
            case SUSPENDED, TERMINATED ->
                assertEquals(ERROR_REASON, notification.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "reason"))).body());
            case COMPLETED, RESUMED -> {
                // Nothing else
            }
        }
    }
}
