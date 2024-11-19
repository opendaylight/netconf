/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class SubtreeFilterRestconfTest {

    @Test
    void testFilterNotification() {
        final var notificationQname = QName.create("urn:ietf:params:xml:ns:netconf:notification:1.0", "notification");
        final var sessionEndQname = QName.create("urn:ietf:params:xml:ns:yang:ietf-netconf-notifications",
            "netconf-session-end");
        final var usernameQname = QName.create(sessionEndQname, "username");
        final var sessionIdQname = QName.create(sessionEndQname, "session-id");
        final var sourceHostQname = QName.create(sessionEndQname, "source-host");
        final var eventTimeQname = QName.create(notificationQname, "eventTime");

        // Create a notification node
        final var sessionEnd = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(sessionEndQname))
            .withChild(ImmutableNodes.leafNode(usernameQname, "admin"))
            .withChild(ImmutableNodes.leafNode(sessionIdQname, 2))
            .withChild(ImmutableNodes.leafNode(sourceHostQname, "127.0.0.1"))
            .build();
        final var notification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(notificationQname))
            .withChild(sessionEnd)
            .withChild(ImmutableNodes.leafNode(eventTimeQname, "2016-03-17T13:15:12+01:00"))
            .build();

        // Create a filter node
        final var filterQname = QName.create("urn:ietf:params:xml:ns:netconf:notification:1.0",
            "stream-subtree-filter");

        final var filterSessionEnd = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(sessionEndQname))
            .withChild(ImmutableNodes.leafNode(usernameQname, ""))
            .withChild(ImmutableNodes.leafNode(sessionIdQname, ""))
            .build();

        final var filterNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(filterQname))
            .withChild(filterSessionEnd)
            .build();

        final var filteredNotification = SubtreeFilterRestconf.applyFilter(filterNode, notification);

        // Create the expected filtered node
        final var expectedFilteredNotification = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(sessionEndQname))
            .withChild(ImmutableNodes.leafNode(usernameQname, "admin"))
            .withChild(ImmutableNodes.leafNode(sessionIdQname, 2))
            .build();

        // Validate the filtered node
        assertNotNull(filteredNotification, "Filtered node should not be null");
        assertEquals(expectedFilteredNotification, filteredNotification);
    }


}
