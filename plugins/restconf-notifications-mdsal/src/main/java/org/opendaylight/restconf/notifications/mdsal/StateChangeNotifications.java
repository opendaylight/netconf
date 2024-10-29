/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMPublishNotificationService;
import org.opendaylight.yang.svc.v1.urn.opendaylight.netconf.keystore.rev240708.YangModuleInfoImpl;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;


public class StateChangeNotifications {
    private static final String SUBSCRIBED_NOTIFICATION = "ietf-subscribed-notifications";
    private static final QName EVENT_TIME = YangModuleInfoImpl.qnameOf("eventTime");
    private static final QName FILTER = YangModuleInfoImpl.qnameOf("stream-xpath-filter");
    private static final QName ID = YangModuleInfoImpl.qnameOf("id");
    private static final QName NETCONF_NOTIFICATION = YangModuleInfoImpl.qnameOf("ietf-subscribed-notifications");
    private static final QName NODE_IDENTIFIER = QName.create("ietf-restconf", "notification");
    private static final QName URI = YangModuleInfoImpl.qnameOf("uri");


    DOMNotificationPublishService service =
        new RouterDOMPublishNotificationService(new DOMNotificationRouter(16));


    public ListenableFuture<?> subscriptionModified(String eventTime, Long id, String uri, String filter)
        throws InterruptedException {
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(SUBSCRIBED_NOTIFICATION, "subscription-modified")))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .withChild(ImmutableNodes.leafNode(URI, uri))
                .withChild(ImmutableNodes.leafNode(FILTER, filter))
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(YangModuleInfoImpl.qnameOf("stream")))
                    .withChild(ImmutableNodes.leafNode(NETCONF_NOTIFICATION, "NETCONF"))
                    .build())
                .build())
            .build();

        DOMNotification notification = new RestconfNotification(node);
        return service.putNotification(notification);
    }


    public ListenableFuture<?> subscriptionCompleted(String eventTime, Long id) throws InterruptedException {
        return subscriptionCompletedResumed(eventTime, id, "subscription-completed");
    }

    public ListenableFuture<?> subscriptionResumed(String eventTime, Long id) throws InterruptedException {
        return subscriptionCompletedResumed(eventTime, id, "subscription-resumed");
    }

    private ListenableFuture<?> subscriptionCompletedResumed(String eventTime, Long id, String action)
        throws InterruptedException {
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(SUBSCRIBED_NOTIFICATION, action)))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .build())
            .build();

        DOMNotification notification = new RestconfNotification(node);
        return service.putNotification(notification);
    }

    public ListenableFuture<?> subscriptionTerminated(String eventTime, Long id, String errorId)
        throws InterruptedException {
        return subscriptionTerminatedSuspended(eventTime, id, errorId, "subscription-terminated");
    }

    public ListenableFuture<?> subscriptionSuspended(String eventTime, Long id, String errorId)
        throws InterruptedException {
        return subscriptionTerminatedSuspended(eventTime, id, errorId, "subscription-resumed");
    }

    private ListenableFuture<?> subscriptionTerminatedSuspended(String eventTime, Long id, String errorId,
        String action) throws InterruptedException {
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(SUBSCRIBED_NOTIFICATION, action)))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .withChild(ImmutableNodes.leafNode(YangModuleInfoImpl.qnameOf("error-id"), errorId))
                .build())
            .build();

        DOMNotification notification = new RestconfNotification(node);
        return service.putNotification(notification);
    }
}
