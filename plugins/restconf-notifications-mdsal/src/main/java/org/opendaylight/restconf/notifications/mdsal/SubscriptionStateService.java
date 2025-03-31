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
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117.Subscription1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Encoding;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionPolicy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspendedReason;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminatedReason;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.dynamic.Stream1;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedAnydata;
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
        NodeIdentifier.create(QName.create(SubscriptionCompleted.QNAME, "id").intern());
    /**
     * {@link NodeIdentifier} of {@code encoding} as expressed in {@link SubscriptionPolicy#getEncoding()}.
     */
    private static final NodeIdentifier ENCODING_NODEID = NodeIdentifier.create(Encoding.QNAME);
    /**
     * {@link NodeIdentifier} of {@code leaf reason} in {@link SubscriptionSuspended} and
     * {@link SubscriptionTerminated}. Value domains are identities derived from {@link SubscriptionSuspendedReason} and
     * {@link SubscriptionTerminatedReason}.
     */
    private static final NodeIdentifier REASON_NODEID =
        NodeIdentifier.create(QName.create(SubscriptionSuspended.QNAME, "reason").intern());
    /**
     * {@link NodeIdentifier} of {@code leaf stream} in {@link SubscriptionModified}. Value domain is all of
     * {@link String} as expressed in {@link Stream1#getStream()}.
     */
    private static final NodeIdentifier STREAM_NODEID =
        NodeIdentifier.create(QName.create(SubscriptionModified.QNAME, "stream").intern());
    /**
     * {@link NodeIdentifier} of {@code stream-subtree-filter} alternative in {@link FilterSpec} as expressed in
     * {@link StreamSubtreeFilter#getStreamSubtreeFilter()}.
     */
    private static final NodeIdentifier SUBTREE_FILTER_NODEID = NodeIdentifier.create(StreamSubtreeFilter.QNAME);
    /**
     * {@link NodeIdentifier} of {@link Subscription1#getUri()}.
     */
    private static final NodeIdentifier URI_NODEID = NodeIdentifier.create(
        org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117
            .YangModuleInfoImpl.qnameOf("uri"));

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
     * @param streamName the subscription stream name
     * @param encoding   the optional subscription encoding
     * @param filter     the optional subscription filter
     * @param uri        the optional subscription uri
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionModified(final Instant eventTime, final Uint32 id, final String streamName,
            final @Nullable QName encoding, final @Nullable NormalizedAnydata filter, final @Nullable String uri)
            throws InterruptedException {
        LOG.debug("Publishing subscription modified notification for ID: {}", id);
        var body = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(State.MODIFIED.nodeId)
            .withChild(ImmutableNodes.leafNode(ID_NODEID, id))
            .withChild(ImmutableNodes.leafNode(STREAM_NODEID, streamName));
        if (encoding != null) {
            body.withChild(ImmutableNodes.leafNode(ENCODING_NODEID, encoding));
        }
        if (filter != null) {
            body.withChild(ImmutableNodes.newAnydataBuilder(NormalizedAnydata.class)
                .withNodeIdentifier(SUBTREE_FILTER_NODEID)
                .withValue(filter)
                .build());
        }
        if (uri != null) {
            body.withChild(ImmutableNodes.leafNode(URI_NODEID, uri));
        }
        return sendNotification(eventTime, body.build());
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
        return sendStateNotification(eventTime, id, State.COMPLETED);
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
        return sendStateNotification(eventTime, id, State.RESUMED);
    }

    /**
     * Sends a notification indicating the subscription was terminated.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @param errorReason   the error ID associated with termination
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionTerminated(final Instant eventTime, final Uint32 id, final QName errorReason)
            throws InterruptedException {
        return sendErrorStateNotification(eventTime, id, errorReason, State.TERMINATED);
    }

    /**
     * Sends a notification indicating the subscription was suspended.
     *
     * @param eventTime the event timestamp
     * @param id        the subscription ID
     * @param errorReason   the error ID associated with suspension
     * @return a listenable future outcome of the notification
     */
    public ListenableFuture<?> subscriptionSuspended(final Instant eventTime, final Uint32 id, final QName errorReason)
            throws InterruptedException {
        return sendErrorStateNotification(eventTime, id, errorReason, State.SUSPENDED);
    }

    /**
     * Builds and sends a generic state notification.
     */
    private ListenableFuture<?> sendStateNotification(final Instant eventTime, final Uint32 id, final State state)
            throws InterruptedException {
        LOG.info("Publishing {} notification for ID: {}", state, id);
        var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(state.nodeId)
            .withChild(ImmutableNodes.leafNode(ID_NODEID, id))
            .build();
        return sendNotification(eventTime, node);
    }

    /**
     * Builds and sends an error state notification.
     */
    private ListenableFuture<?> sendErrorStateNotification(final Instant eventTime, final Uint32 id,
            final QName errorReason, final State state) throws InterruptedException {
        LOG.info("Publishing {} notification for ID: {} with error ID: {}", state, id, errorReason);
        var node = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(state.nodeId)
            .withChild(ImmutableNodes.leafNode(ID_NODEID, id))
            .withChild(ImmutableNodes.leafNode(REASON_NODEID, errorReason))
            .build();
        return sendNotification(eventTime, node);
    }

    /**
     * Sends the notification through the publishing service.
     */
    private ListenableFuture<?> sendNotification(final Instant eventTime, final ContainerNode node)
            throws InterruptedException {
        final var notification = new DOMNotificationEvent.Rfc6020(node, eventTime);
        return publishService.putNotification(notification);
    }
}
