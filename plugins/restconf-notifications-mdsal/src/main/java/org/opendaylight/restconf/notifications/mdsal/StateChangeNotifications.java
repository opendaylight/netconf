/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class StateChangeNotifications {
    private static final String COMPLETED = "subscription-completed";
    private static final String RESUMED = "subscription-resumed";
    private static final String MODIFIED = "subscription-modified";
    private static final String TERMINATED = "subscription-terminated";
    private static final String SUSPENDED = "subscription-suspended";
    private static final String SUBSCRIBED_NOTIFICATION = "ietf-subscribed-notifications";
    private static final QName QNAME = QName.create("urn:opendaylight:restconf:notifications",
        "restconf-notifications").intern();
    private static final QName EVENT_TIME = QName.create(QNAME, "eventTime");
    private static final QName FILTER = QName.create(QNAME, "stream-xpath-filter");
    private static final QName ID = QName.create(QNAME, "id");
    private static final QName NETCONF_NOTIFICATION = QName.create(QNAME, "ietf-subscribed-notifications");
    private static final QName NODE_IDENTIFIER = QName.create("ietf-restconf", "notification");
    private static final QName URI = QName.create(QNAME, "uri");

    private final DOMNotificationPublishService service;

    @Inject
    @Activate
    public StateChangeNotifications(@Reference final DOMNotificationPublishService publishService) {
        this.service = publishService;
    }


    public ListenableFuture<?> subscriptionModified(String eventTime, Long id, String uri, String filter)
        throws InterruptedException {
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(SUBSCRIBED_NOTIFICATION, MODIFIED)))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .withChild(ImmutableNodes.leafNode(URI, uri))
                .withChild(ImmutableNodes.leafNode(FILTER, filter))
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(QName.create(QNAME,"stream")))
                    .withChild(ImmutableNodes.leafNode(NETCONF_NOTIFICATION, "NETCONF"))
                    .build())
                .build())
            .build();

        DOMNotification notification = new RestconfNotification(node);
        return service.putNotification(notification);
    }


    public ListenableFuture<?> subscriptionCompleted(String eventTime, Long id) throws InterruptedException {
        return subscriptionCompletedResumed(eventTime, id, COMPLETED);
    }

    public ListenableFuture<?> subscriptionResumed(String eventTime, Long id) throws InterruptedException {
        return subscriptionCompletedResumed(eventTime, id, RESUMED);
    }

    private ListenableFuture<?> subscriptionCompletedResumed(String eventTime, Long id, String notificationType)
        throws InterruptedException {
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(SUBSCRIBED_NOTIFICATION, notificationType)))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .build())
            .build();

        DOMNotification notification = new RestconfNotification(node);
        return service.putNotification(notification);
    }

    public ListenableFuture<?> subscriptionTerminated(String eventTime, Long id, String errorId)
        throws InterruptedException {
        return subscriptionTerminatedSuspended(eventTime, id, errorId, TERMINATED);
    }

    public ListenableFuture<?> subscriptionSuspended(String eventTime, Long id, String errorId)
        throws InterruptedException {
        return subscriptionTerminatedSuspended(eventTime, id, errorId, SUSPENDED);
    }

    private ListenableFuture<?> subscriptionTerminatedSuspended(String eventTime, Long id, String errorId,
        String notificationType) throws InterruptedException {
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(SUBSCRIBED_NOTIFICATION, notificationType)))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .withChild(ImmutableNodes.leafNode(QName.create(QNAME,"error-id"), errorId))
                .build())
            .build();

        DOMNotification notification = new RestconfNotification(node);
        return service.putNotification(notification);
    }
}
