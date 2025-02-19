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
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.BASE_QNAME;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.COMPLETED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.MODIFIED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.NETCONF_NOTIFICATION;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.REASON;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.RESUMED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.SUSPENDED;
import static org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService.TERMINATED;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.mdsal.testkit.MdsalTestkit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117.IetfRestconfSubscribedNotificationsData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.IetfSubscribedNotificationsData;
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
    private static final String ERROR_REASON = "example-error-reason";

    private static MdsalTestkit TESTKIT;

    @Captor
    private ArgumentCaptor<DOMNotification> captor;

    private final SubscriptionStateService notifications =
        new SubscriptionStateService(TESTKIT.domNotificationPublishService());

    @BeforeAll
    static void beforeAll() {
        TESTKIT = MdsalTestkit.builder(IetfRestconfSubscribedNotificationsData.class,
            IetfSubscribedNotificationsData.class).build();
    }

    @AfterAll
    static void afterAll() {
        TESTKIT.close();
    }

    private enum SubscriptionType {
        MODIFIED, COMPLETED, RESUMED, TERMINATED, SUSPENDED
    }

    @ParameterizedTest
    @EnumSource(SubscriptionType.class)
    void testSubscriptionEvents(final SubscriptionType type) throws InterruptedException {
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
                notifications.subscriptionTerminated(EVENT_TIME, ID, ERROR_REASON);
                yield QName.create(NETCONF_NOTIFICATION, TERMINATED);
            }
            case SUSPENDED -> {
                notifications.subscriptionSuspended(EVENT_TIME, ID, ERROR_REASON);
                yield QName.create(NETCONF_NOTIFICATION, SUSPENDED);
            }
        };

        verify(mockPublishService).putNotification(captor.capture());
        final var notification = captor.getValue();
        assertNotNull(notification);
        assertEquals(EVENT_TIME, notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.EVENT_TIME)).body());

        final var eventNode = (ContainerNode) notification.getBody()
            .getChildByArg(new YangInstanceIdentifier.NodeIdentifier(eventQName));

        assertNotNull(eventNode);
        assertEquals(ID, eventNode.getChildByArg(
            new YangInstanceIdentifier.NodeIdentifier(SubscriptionStateService.ID)).body());

        if (SubscriptionType.MODIFIED == type) {
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

        if (SubscriptionType.TERMINATED == type || SubscriptionType.SUSPENDED == type) {
            assertEquals(ERROR_REASON, eventNode.getChildByArg(
                new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE_QNAME, REASON))).body());
        }
    }
}
