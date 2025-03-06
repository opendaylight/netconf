/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Filters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.stream.subtree.filter.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;

public final class SubscriptionUtil {
    public static final YangInstanceIdentifier SUBSCRIPTIONS = YangInstanceIdentifier.of(
        NodeIdentifier.create(Subscriptions.QNAME),
        NodeIdentifier.create(Subscription.QNAME));
    public static final YangInstanceIdentifier STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(Streams.QNAME),
        NodeIdentifier.create(Stream.QNAME));
    public static final YangInstanceIdentifier FILTERS = YangInstanceIdentifier.of(
        NodeIdentifier.create(Filters.QNAME),
        NodeIdentifier.create(StreamFilter.QNAME));
    public static final QName QNAME_ID = QName.create(Subscription.QNAME, "id");
    public static final QName QNAME_STREAM = QName.create(Subscription.QNAME, "stream");
    public static final QName QNAME_STREAM_FILTER = QName.create(Subscription.QNAME, "stream-filter-name");
    public static final QName QNAME_ENCODING = QName.create(Subscription.QNAME, "encoding");
    public static final QName QNAME_STREAM_NAME = QName.create(Stream.QNAME, "name");
    public static final QName QNAME_STREAM_FILTER_NAME = QName.create(StreamFilter.QNAME, "name");
    public static final QName QNAME_RECEIVER_NAME = QName.create(Receiver.QNAME, "name");
    public static final QName QNAME_RECEIVER_STATE = QName.create(Receiver.QNAME, "state");
    public static final QName QNAME_TARGET = QName.create(Subscription.QNAME, "target");
    public static final QName QNAME_SENT_EVENT_RECORDS = QName.create(Receiver.QNAME, "sent-event-records");
    public static final QName QNAME_EXCLUDED_EVENT_RECORDS = QName
        .create(Receiver.QNAME, "excluded-event-records");
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER_NAME =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream-filter-name").intern());

    private SubscriptionUtil() {
        // hidden on purpose
    }

    static @Nullable SubscriptionFilter extractFilter(final ChoiceNode streamFilter) {
        if (streamFilter.childByArg(SUBSCRIPTION_STREAM_FILTER_NAME) instanceof LeafNode<?> leafNode) {
            if (leafNode.body() instanceof String filterName) {
                return new SubscriptionFilter.Reference(filterName);
            }
            throw new IllegalArgumentException("Bad child " + leafNode.prettyTree());
        }
        final var filterSpec = (ChoiceNode) streamFilter.childByArg(new NodeIdentifier(FilterSpec.QNAME));
        if (filterSpec == null) {
            return null;
        }
        final var subtree = (AnydataNode<?>) filterSpec.childByArg(new NodeIdentifier(StreamSubtreeFilter.QNAME));
        if (subtree != null) {
            return new SubscriptionFilter.SubtreeDefinition(subtree);
        }
        if (filterSpec.childByArg(new NodeIdentifier(QName.create(FilterSpec.QNAME, "stream-xpath-filter")))
            instanceof LeafNode<?> leafNode) {
            if (leafNode.body() instanceof String xpath) {
                return new SubscriptionFilter.XPathDefinition(xpath);
            }
            throw new IllegalArgumentException("Bad child " + leafNode.prettyTree());
        }
        return null;
    }
}
