/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing subscription state notifications.
 */
@Component
public class SubscriptionStateService {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionStateService.class);

    // Subscription states
    static final String COMPLETED = "subscription-completed";
    static final String RESUMED = "subscription-resumed";
    static final String MODIFIED = "subscription-modified";
    static final String TERMINATED = "subscription-terminated";
    static final String SUSPENDED = "subscription-suspended";

    // Notification types
    private static final QName RESTCONF_NOTIF_NODE_IDENTIFIER = QName.create("ietf-restconf", "notification");
    static final QName BASE_QNAME = QName.create("urn:opendaylight:restconf:notifications",
        "restconf-notifications").intern();
    static final QName EVENT_TIME = QName.create(BASE_QNAME, "eventTime");
    static final QName FILTER = QName.create(BASE_QNAME, "stream-xpath-filter");
    static final QName ID = QName.create(BASE_QNAME, "id");
    static final QName NETCONF_NOTIFICATION = QName.create(BASE_QNAME, "ietf-subscribed-notifications");
    static final QName URI = QName.create(BASE_QNAME, "uri");

    private final DOMNotificationPublishService publishService;

    @Inject
    @Activate
    public SubscriptionStateService(@Reference final DOMNotificationPublishService publishService) {
        this.publishService = publishService;
    }

    /**
     * Sends a notification indicating the subscription was modified.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @param uri       the subscription URI
     * @param filter    the subscription filter
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionModified(String eventTime, Long id, String uri, String filter)
            throws InterruptedException {
        LOG.info("Publishing subscription modified notification for ID: {}", id);
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(RESTCONF_NOTIF_NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(NETCONF_NOTIFICATION, MODIFIED)))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .withChild(ImmutableNodes.leafNode(URI, uri))
                .withChild(ImmutableNodes.leafNode(FILTER, filter))
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(QName.create(BASE_QNAME, "stream")))
                    .withChild(ImmutableNodes.leafNode(
                        QName.create(BASE_QNAME, "ietf-netconf-subscribed-notifications"), "NETCONF"))
                    .build())
                .build())
            .build();
        return sendNotification(node);
    }

    /**
     * Sends a notification indicating the subscription was completed.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionCompleted(String eventTime, Long id) throws InterruptedException {
        return sendStateNotification(eventTime, id, COMPLETED);
    }

    /**
     * Sends a notification indicating the subscription was resumed.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionResumed(String eventTime, Long id) throws InterruptedException {
        return sendStateNotification(eventTime, id, RESUMED);
    }

    /**
     * Sends a notification indicating the subscription was terminated.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @param errorId   the error ID associated with termination
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionTerminated(String eventTime, Long id, String errorId)
            throws InterruptedException {
        return sendErrorStateNotification(eventTime, id, errorId, TERMINATED);
    }

    /**
     * Sends a notification indicating the subscription was suspended.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @param errorId   the error ID associated with suspension
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionSuspended(String eventTime, Long id, String errorId)
            throws InterruptedException {
        return sendErrorStateNotification(eventTime, id, errorId, SUSPENDED);
    }

    /**
     * Builds and sends a generic state notification.
     */
    private ListenableFuture<?> sendStateNotification(String eventTime, Long id, String state)
            throws InterruptedException {
        LOG.info("Publishing {} notification for ID: {}", state, id);
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(RESTCONF_NOTIF_NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(NETCONF_NOTIFICATION, state)))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .build())
            .build();
        return sendNotification(node);
    }

    /**
     * Builds and sends an error state notification.
     */
    private ListenableFuture<?> sendErrorStateNotification(String eventTime, Long id, String errorId, String state)
            throws InterruptedException {
        LOG.info("Publishing {} notification for ID: {} with error ID: {}", state, id, errorId);
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(RESTCONF_NOTIF_NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(NETCONF_NOTIFICATION, state)))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .withChild(ImmutableNodes.leafNode(QName.create(BASE_QNAME, "error-id"), errorId))
                .build())
            .build();
        return sendNotification(node);
    }

    /**
     * Sends the notification through the publishing service.
     */
    private ListenableFuture<?> sendNotification(ContainerNode node) throws InterruptedException {
        final var notification = new RestconfNotification(node);
        return publishService.putNotification(notification);
    }
}
