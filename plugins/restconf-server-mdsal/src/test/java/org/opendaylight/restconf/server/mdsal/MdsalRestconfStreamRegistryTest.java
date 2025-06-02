/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminatedReason;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;

public class MdsalRestconfStreamRegistryTest {
    private static final Uint32 ID = Uint32.valueOf(2147483648L);
    private static final String STOP_TIME = "2024-10-30T12:34:56Z";
    private static final String STREAM_NAME = "NETCONF";
    private static final String URI = "http://example.com";
    private static final QName ENCODING = EncodeXml$I.QNAME;
    private static final QName ERROR_REASON = SubscriptionTerminatedReason.QNAME;
    private static final NodeIdentifier URI_NODEID = NodeIdentifier.create(
        org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117
            .YangModuleInfoImpl.qnameOf("uri"));

    @ParameterizedTest
    @EnumSource(MdsalRestconfStreamRegistry.State.class)
    void testSubscriptionStateEventsXml(final MdsalRestconfStreamRegistry.State type) {
        final var notification = switch (type) {
            case MODIFIED -> MdsalRestconfStreamRegistry.subscriptionModified(ID, STREAM_NAME, ENCODING, null,
                STOP_TIME, URI);
            case RESUMED -> MdsalRestconfStreamRegistry.subscriptionResumed(ID);
            case TERMINATED -> MdsalRestconfStreamRegistry.subscriptionTerminated(ID, ERROR_REASON);
            case SUSPENDED -> MdsalRestconfStreamRegistry.subscriptionSuspended(ID, ERROR_REASON);
        };

        final var eventQName = type.nodeId.getNodeType();
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
                assertEquals(STOP_TIME, notification.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "stop-time"))).body());
                assertEquals(URI, notification.getChildByArg(URI_NODEID).body());
            }
            case SUSPENDED, TERMINATED ->
                assertEquals(ERROR_REASON, notification.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "reason"))).body());
            case RESUMED -> {
                // Nothing else
            }
        }
    }
}
