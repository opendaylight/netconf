/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry.SubscriptionControl;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamXpathFilter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

/**
 * A {@link SubscriptionControl} updating the MD-SAL datastore.
 */
@NonNullByDefault
final class MdsalSubscriptionControl implements SubscriptionControl {
    private static final NodeIdentifier FILTER_SPEC_NODEID = NodeIdentifier.create(FilterSpec.QNAME);
    private static final NodeIdentifier STREAM_FILTER_NAME_NODEID =
        NodeIdentifier.create(QName.create(FilterSpec.QNAME, "stream-filter-name").intern());
    private static final NodeIdentifier STREAM_XPATH_FILTER_NODEID = NodeIdentifier.create(StreamXpathFilter.QNAME);
    private static final NodeIdentifier STREAM_SUBTREE_FILTER_NODEID = NodeIdentifier.create(StreamSubtreeFilter.QNAME);

    private final DOMDataBroker dataBroker;
    private final Uint32 subscriptionId;

    MdsalSubscriptionControl(final DOMDataBroker dataBroker, final Uint32 subscriptionId) {
        this.dataBroker = requireNonNull(dataBroker);
        this.subscriptionId = requireNonNull(subscriptionId);
    }

    @Override
    public ListenableFuture<@Nullable Void> terminate() {
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, MdsalRestconfStreamRegistry.subscriptionPath(subscriptionId));
        return mapFuture(tx.commit());
    }

    @Override
    public ListenableFuture<@Nullable Void> updateFilter(final @Nullable SubscriptionFilter newFilter) {
        return mapFuture(newFilter == null ? deleteFilter() : setFilter(newFilter));
    }

    private FluentFuture<? extends CommitInfo> deleteFilter() {
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, MdsalRestconfStreamRegistry.streamFilterPath(subscriptionId));
        return tx.commit();
    }

    private FluentFuture<? extends CommitInfo> setFilter(final SubscriptionFilter filter) {
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, MdsalRestconfStreamRegistry.streamFilterPath(subscriptionId),
            switch (filter) {
                case SubscriptionFilter.Reference(var filterName) ->
                    ImmutableNodes.leafNode(STREAM_FILTER_NAME_NODEID, filterName);
                case SubscriptionFilter.SubtreeDefinition(var anydata) ->
                    ImmutableNodes.newChoiceBuilder()
                        .withNodeIdentifier(FILTER_SPEC_NODEID)
                        .withChild(ImmutableNodes.leafNode(STREAM_SUBTREE_FILTER_NODEID, anydata))
                        .build();
                case SubscriptionFilter.XPathDefinition(final var xpath) ->
                    ImmutableNodes.newChoiceBuilder()
                        .withNodeIdentifier(FILTER_SPEC_NODEID)
                        .withChild(ImmutableNodes.leafNode(STREAM_XPATH_FILTER_NODEID, xpath))
                        .build();
            });
        return tx.commit();
    }

    private static ChoiceNode wrapFilterSpec(LeafNode<?>)

    private static ListenableFuture<@Nullable Void> mapFuture(final FluentFuture<? extends CommitInfo> future) {
        return future.transform(unused -> null, MoreExecutors.directExecutor());
    }
}
