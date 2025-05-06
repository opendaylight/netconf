/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import com.google.common.annotations.VisibleForTesting;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.stream.subtree.filter.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;

final class SubscriptionUtil {
    @VisibleForTesting
    static final QName QNAME_ID = QName.create(Subscription.QNAME, "id");

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
