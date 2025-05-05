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

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminatedReason;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

@ExtendWith(MockitoExtension.class)
class SubscriptionStateServiceTest {
    private static final Uint32 ID = Uint32.valueOf(2147483648L);
    private static final String STOP_TIME = "2024-10-30T12:34:56Z";
    private static final String STREAM_NAME = "NETCONF";
    private static final String URI = "http://example.com";
    private static final QName ENCODING = EncodeXml$I.QNAME;
    private static final QName ERROR_REASON = SubscriptionTerminatedReason.QNAME;
    private static final NodeIdentifier URI_NODEID = NodeIdentifier.create(
        org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117
            .YangModuleInfoImpl.qnameOf("uri"));

    enum SubscriptionType {
        MODIFIED, COMPLETED, RESUMED, TERMINATED, SUSPENDED
    }

    @ParameterizedTest
    @EnumSource(SubscriptionType.class)
    void testSubscriptionEvents(final SubscriptionType type) throws InterruptedException {
        final ContainerNode notificationNode;

        final QName eventQName = switch (type) {
            case MODIFIED -> {
                notificationNode = SubscriptionStateService.subscriptionModified(ID, STREAM_NAME, ENCODING, null,
                    STOP_TIME, URI);
                yield SubscriptionModified.QNAME;
            }
            case COMPLETED -> {
                notificationNode = SubscriptionStateService.subscriptionCompleted(ID);
                yield SubscriptionCompleted.QNAME;
            }
            case RESUMED -> {
                notificationNode = SubscriptionStateService.subscriptionResumed(ID);
                yield SubscriptionResumed.QNAME;
            }
            case TERMINATED -> {
                notificationNode = SubscriptionStateService.subscriptionTerminated(ID, ERROR_REASON);
                yield SubscriptionTerminated.QNAME;
            }
            case SUSPENDED -> {
                notificationNode = SubscriptionStateService.subscriptionSuspended(ID, ERROR_REASON);
                yield SubscriptionSuspended.QNAME;
            }
        };
        assertNotNull(notificationNode);
        assertEquals(ID, notificationNode.getChildByArg(
            new NodeIdentifier(QName.create(eventQName, "id"))).body());

        switch (type) {
            case null -> throw new NullPointerException();
            case MODIFIED -> {
                assertEquals(ENCODING, notificationNode.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "encoding"))).body());
                assertEquals(STREAM_NAME, notificationNode.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "stream"))).body());
                assertEquals(STOP_TIME, notificationNode.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "stop-time"))).body());
                assertEquals(URI, notificationNode.getChildByArg(URI_NODEID).body());
            }
            case SUSPENDED, TERMINATED ->
                assertEquals(ERROR_REASON, notificationNode.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "reason"))).body());
            case COMPLETED, RESUMED -> {
                // Nothing else
            }
        }
    }
}
