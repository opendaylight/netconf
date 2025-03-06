/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ForwardingRestconfStreamSubscription;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.subscription.SubscriptionUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.target.stream.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MdsalRestconfStreamSubscription<T extends RestconfStream.Subscription>
        extends ForwardingRestconfStreamSubscription<T> {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfStreamSubscription.class);

    private final DOMDataBroker dataBroker;

    MdsalRestconfStreamSubscription(final T delegate, final DOMDataBroker dataBroker) {
        super(delegate);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    public void modifyFilter(final ServerRequest<Empty> request, final RestconfStream.SubscriptionFilter filter) {
        delegate.modifyFilter(request, filter);
        final DataContainerChild filterNode = switch (filter) {
            case RestconfStream.SubscriptionFilter.Reference(var filterName) ->
                ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM_FILTER, filterName);
            case RestconfStream.SubscriptionFilter.SubtreeDefinition(var anydata) ->
                ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(FilterSpec.QNAME))
                    .withChild(anydata)
                    .build();
            case RestconfStream.SubscriptionFilter.XPathDefinition(final var xpath) ->
                ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(FilterSpec.QNAME))
                    .withChild(ImmutableNodes.leafNode(QName.create(FilterSpec.QNAME, "stream-xpath-filter"), xpath))
                    .build();
        };

        final var tx = dataBroker.newWriteOnlyTransaction();
        final var nodeId = NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, id());
        tx.merge(LogicalDatastoreType.OPERATIONAL, SubscriptionUtil.SUBSCRIPTIONS.node(nodeId),
            ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(nodeId)
                .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, id()))
                .withChild(ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(SubscriptionUtil.QNAME_TARGET))
                        .withChild(ImmutableNodes.newChoiceBuilder()
                            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(StreamFilter.QNAME))
                            .withChild(filterNode)
                            .build())
                .build())
            .build());
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Modified subscription {} in operational datastore as of {}", id(), result);
                request.completeWith(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("Failed to modify subscription {} in operational datastore", id(), cause);
                request.completeWith(new RequestException(cause));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected void terminateImpl(final ServerRequest<Empty> request, final QName terminationReason) {
        final var id = id();
        LOG.debug("{} terminated with reason {}", id, terminationReason);

        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, SubscriptionUtil.SUBSCRIPTIONS.node(
            NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, id)));
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Removed subscription {} from operational datastore as of {}", id, result);
                request.completeWith(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("Failed to remove subscription {} from operational datastore", id, cause);
                request.completeWith(new RequestException(cause));
            }
        }, MoreExecutors.directExecutor());
    }
}
