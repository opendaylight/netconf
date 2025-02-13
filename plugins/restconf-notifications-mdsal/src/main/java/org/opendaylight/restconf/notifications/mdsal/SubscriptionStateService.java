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
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
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
@Singleton
@Component(service = SubscriptionStateService.class)
public class SubscriptionStateService {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionStateService.class);

    // Subscription states
    static final String COMPLETED = "subscription-completed";
    static final String RESUMED = "subscription-resumed";
    static final String TERMINATED = "subscription-terminated";
    static final String SUSPENDED = "subscription-suspended";
    static final String REASON = "reason";
    static final String ID = "id";

    private final DOMNotificationPublishService publishService;

    @Inject
    @Activate
    public SubscriptionStateService(@Reference final DOMNotificationPublishService publishService) {
        this.publishService = publishService;
    }

    /**
     * Sends a notification indicating the subscription was modified.
     *
     * @param id         the subscription ID
     * @param streamName the subscription stream name
     * @param encoding   the optional subscription encoding
     * @param filter     the optional subscription filter
     * @param stopTime   the optional subscription stop time
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionModified(final Long id, final String streamName,
            final @Nullable String encoding, final @Nullable String filter,
            final @Nullable DateAndTime stopTime) throws InterruptedException {
        LOG.debug("Publishing subscription modified notification for ID: {}", id);
        var body = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(SubscriptionModified.QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create(SubscriptionModified.QNAME, ID).intern(), id))
            .withChild(ImmutableNodes.leafNode(QName.create(SubscriptionModified.QNAME, "stream")
                .intern(), streamName));
        if (encoding != null) {
            body.withChild(ImmutableNodes.leafNode(QName.create(SubscriptionModified.QNAME, "encoding")
                .intern(), encoding));
        }
        if (filter != null) {
            body.withChild(ImmutableNodes.leafNode(QName.create(SubscriptionModified.QNAME, "stream-subtree-filter")
                .intern(), filter));
        }
        if (stopTime != null) {
            body.withChild(ImmutableNodes.leafNode(QName.create(SubscriptionModified.QNAME, "stop-time")
                .intern(), stopTime));
        }
        return sendNotification(body.build());
    }

    /**
     * Sends a notification indicating the subscription was completed.
     *
     * @param id        the subscription ID
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionCompleted(final Long id)
            throws InterruptedException {
        return sendStateNotification(SubscriptionCompleted.QNAME, id, COMPLETED);
    }

    /**
     * Sends a notification indicating the subscription was resumed.
     *
     * @param id        the subscription ID
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionResumed(final Long id) throws InterruptedException {
        return sendStateNotification(SubscriptionResumed.QNAME, id, RESUMED);
    }

    /**
     * Sends a notification indicating the subscription was terminated.
     *
     * @param id        the subscription ID
     * @param errorReason   the error ID associated with termination
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionTerminated(final Long id, final QName errorReason)
            throws InterruptedException {
        return sendErrorStateNotification(SubscriptionTerminated.QNAME, id, errorReason, TERMINATED);
    }

    /**
     * Sends a notification indicating the subscription was suspended.
     *
     * @param id        the subscription ID
     * @param errorReason   the error ID associated with suspension
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionSuspended(final  Long id, final QName errorReason)
            throws InterruptedException {
        return sendErrorStateNotification(SubscriptionSuspended.QNAME, id, errorReason, SUSPENDED);
    }

    /**
     * Builds and sends a generic state notification.
     */
    private ListenableFuture<?> sendStateNotification(final QName qname, final Long id, final String state)
            throws InterruptedException {
        LOG.info("Publishing {} notification for ID: {}", state, id);
        var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(qname))
            .withChild(ImmutableNodes.leafNode(QName.create(qname, ID).intern(), id))
            .build();
        return sendNotification(node);
    }

    /**
     * Builds and sends an error state notification.
     */
    private ListenableFuture<?> sendErrorStateNotification(final QName qname, final Long id,
            final QName errorReason, final String state) throws InterruptedException {
        LOG.info("Publishing {} notification for ID: {} with error ID: {}", state, id, errorReason);
        var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(qname))
            .withChild(ImmutableNodes.leafNode(QName.create(qname, ID).intern(), id))
            .withChild(ImmutableNodes.leafNode(QName.create(qname, REASON).intern(), errorReason))
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
