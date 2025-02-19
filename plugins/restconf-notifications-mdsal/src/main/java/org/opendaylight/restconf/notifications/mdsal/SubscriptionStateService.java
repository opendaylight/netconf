/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import com.google.common.util.concurrent.ListenableFuture;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117.Subscription1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspendedReason;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminatedReason;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamXpathFilter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
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
    /**
     * Subscription states. Each is backed by a notification in {@code ietf-subscribed-notifications}.
     */
    @NonNullByDefault
    private enum State {
        COMPLETED(SubscriptionCompleted.QNAME),
        RESUMED(SubscriptionResumed.QNAME),
        MODIFIED(SubscriptionModified.QNAME),
        TERMINATED(SubscriptionTerminated.QNAME),
        SUSPENDED(SubscriptionSuspended.QNAME);

        final NodeIdentifier nodeId;

        State(final QName qname) {
            nodeId = NodeIdentifier.create(qname);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionStateService.class);

    /**
     * {@link NodeIdentifier} of {@code leaf id} in {@link SubscriptionCompleted} et al. Value domain is all of
     * {@link Uint32} as expressed in {@link SubscriptionId}.
     */
    private static final NodeIdentifier ID_NODEID =
        NodeIdentifier.create(QName.create(SubscriptionCompleted.QNAME, "id"));
    /**
     * {@link NodeIdentifier} of {@code leaf reason} in {@link SubscriptionSuspended} and
     * {@link SubscriptionTerminated}. Value domains are identities derived from {@link SubscriptionSuspendedReason} and
     * {@link SubscriptionTerminatedReason}.
     */
    private static final NodeIdentifier REASON_NODEID =
        NodeIdentifier.create(QName.create(SubscriptionSuspended.QNAME, "reason"));
    /**
     * {@link NodeIdentifier} of {@code leaf stream-xpath-filter} alternative in {@link FilterSpec} as expressed in
     * {@link StreamXpathFilter#getStreamXpathFilter()}.
     */
    private static final NodeIdentifier STREAM_XPATH_FILTER_NODEID = NodeIdentifier.create(StreamXpathFilter.QNAME);
    /**
     * {@link NodeIdentifier} of {@link Subscription1#getUri()}.
     */
    private static final NodeIdentifier URI_NODEID = NodeIdentifier.create(
        org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117
            .YangModuleInfoImpl.qnameOf("uri"));

    // FIXME: eliminate these in favor of State above
    static final String COMPLETED = "subscription-completed";
    static final String RESUMED = "subscription-resumed";
    static final String MODIFIED = "subscription-modified";
    static final String TERMINATED = "subscription-terminated";
    static final String SUSPENDED = "subscription-suspended";
    static final String REASON = "reason";

    // FIXME: eliminate these in favor of State.nodeId, ID_NODEID, REASON_NODEID, URI_NODEID
    //        and STREAM_XPATH_FILTER_NODEID
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
     * @param eventTime  the event timestamp
     * @param id         the subscription ID
     * @param uri        the subscription URI
     * @param streamName the subscription stream name
     * @param filter     the optional subscription filter
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionModified(final Instant eventTime, final Uint32 id, final String uri,
            final String streamName, final @Nullable String filter) throws InterruptedException {
        LOG.info("Publishing subscription modified notification for ID: {}", id);
        final var builder = ImmutableNodes.newContainerBuilder()
            // FIXME: State.MODIFIED.nodeId
            .withNodeIdentifier(new NodeIdentifier(QName.create(NETCONF_NOTIFICATION, MODIFIED)))
            // FIXME: ID_NODEID
            .withChild(ImmutableNodes.leafNode(ID, id))
            // FIXME: URI_NODEID
            .withChild(ImmutableNodes.leafNode(URI, uri))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(BASE_QNAME, "stream")))
                .withChild(ImmutableNodes.leafNode(
                    QName.create(BASE_QNAME, "ietf-netconf-subscribed-notifications"), streamName))
                .build());
        if (filter != null) {
            // FIXME: STREAM_XPATH_FILTER_NODEID
            builder.withChild(ImmutableNodes.leafNode(FILTER, filter));
        }

        // FIXME: useless encapsulation, eventTime should be passed to sendNotification()
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(RESTCONF_NOTIF_NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime.toString()))
            .withChild(builder.build())
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
    public ListenableFuture<?> subscriptionCompleted(final Instant eventTime, final Uint32 id)
            throws InterruptedException {
        return sendStateNotification(eventTime, id, COMPLETED);
    }

    /**
     * Sends a notification indicating the subscription was resumed.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionResumed(final Instant eventTime, final Uint32 id)
            throws InterruptedException {
        return sendStateNotification(eventTime, id, RESUMED);
    }

    /**
     * Sends a notification indicating the subscription was terminated.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @param reason    a concrete {@link SubscriptionTerminatedReason}
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionTerminated(final Instant eventTime, final Uint32 id,
            final QName reason) throws InterruptedException {
        if (reason.equals(SubscriptionTerminatedReason.QNAME)) {
            throw new IllegalArgumentException("A concrete reason is required");
        }
        return sendErrorStateNotification(eventTime, id, reason, TERMINATED);
    }

    /**
     * Sends a notification indicating the subscription was suspended.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @param reason    a concrete {@link SubscriptionSuspendedReason}
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionSuspended(final Instant eventTime,final Uint32 id, final QName reason)
            throws InterruptedException {
        if (reason.equals(SubscriptionSuspendedReason.QNAME)) {
            throw new IllegalArgumentException("A concrete reason is required");
        }
        return sendErrorStateNotification(eventTime, id, reason, SUSPENDED);
    }

    /**
     * Builds and sends a generic state notification.
     */
    private ListenableFuture<?> sendStateNotification(final Instant eventTime, final Uint32 id, final String state)
            throws InterruptedException {
        LOG.info("Publishing {} notification for ID: {}", state, id);
        // FIXME: useless encapsulation, eventTime should be passed to sendNotification()
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(RESTCONF_NOTIF_NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime.toString()))
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
    private ListenableFuture<?> sendErrorStateNotification(final Instant eventTime, final Uint32 id,
            final QName reason, final String state) throws InterruptedException {
        LOG.info("Publishing {} notification for ID: {} with error ID: {}", state, id, reason);
        // FIXME: useless encapsulation, eventTime should be passed to sendNotification()
        final var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(RESTCONF_NOTIF_NODE_IDENTIFIER))
            .withChild(ImmutableNodes.leafNode(EVENT_TIME, eventTime.toString()))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(NETCONF_NOTIFICATION, state)))
                .withChild(ImmutableNodes.leafNode(ID, id))
                .withChild(ImmutableNodes.leafNode(QName.create(BASE_QNAME, REASON), reason))
                .build())
            .build();
        return sendNotification(node);
    }

    /**
     * Sends the notification through the publishing service.
     */
    // FIXME: should receive eventTime
    private ListenableFuture<?> sendNotification(final ContainerNode node) throws InterruptedException {
        // FIXME: use DOMNotificationEvent.Rfc6020 with eventTime instead
        final var notification = new RestconfNotification(node);
        return publishService.putNotification(notification);
    }
}
