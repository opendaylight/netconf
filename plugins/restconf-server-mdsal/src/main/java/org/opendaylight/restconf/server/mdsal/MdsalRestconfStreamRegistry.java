/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.subscription.SubscriptionUtil.QNAME_EXCLUDED_EVENT_RECORDS;
import static org.opendaylight.restconf.subscription.SubscriptionUtil.QNAME_ID;
import static org.opendaylight.restconf.subscription.SubscriptionUtil.QNAME_RECEIVER_NAME;
import static org.opendaylight.restconf.subscription.SubscriptionUtil.QNAME_SENT_EVENT_RECORDS;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeListener;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ReceiverHolder;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.subscription.SubscriptionUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Filters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.Receivers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This singleton class is responsible for creation, removal and searching for {@link RestconfStream}s.
 */
@Singleton
@Component(service = RestconfStream.Registry.class)
public final class MdsalRestconfStreamRegistry extends AbstractRestconfStreamRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfStreamRegistry.class);

    public static final YangInstanceIdentifier FILTERS = YangInstanceIdentifier.of(
        YangInstanceIdentifier.NodeIdentifier.create(Filters.QNAME),
        YangInstanceIdentifier.NodeIdentifier.create(StreamFilter.QNAME));
    private static final YangInstanceIdentifier.NodeIdentifier FILTER_NAME =
        YangInstanceIdentifier.NodeIdentifier.create(QName.create(StreamFilter.QNAME, "name").intern());

    private final DOMDataBroker dataBroker;
    private final List<StreamSupport> supports;
    //private final DOMDataBroker.@Nullable DataTreeChangeExtension changeService;

    public class FilterDataTreeChangeListener implements DOMDataTreeChangeListener {

        @Override
        public void onDataTreeChanged(@NonNull List<DataTreeCandidate> changes) {
            for (var change : changes) {
                final var node = change.getRootNode();
                switch (node.modificationType()) {
                    //same for everything??
                    case SUBTREE_MODIFIED, APPEARED, WRITE -> {
                        final var data = (MapNode) change.getRootNode().dataAfter();
                        // FIXME: needs some refactoring
                        data.body().forEach(entry -> {
                            final var name = (String) entry.childByArg(FILTER_NAME).body();
                            final var filterSpec = (ChoiceNode) entry.childByArg(new NodeIdentifier(FilterSpec.QNAME));
                            final var subtree = (AnydataNode<?>) filterSpec
                                .childByArg(new NodeIdentifier(StreamSubtreeFilter.QNAME));
                            final EventStreamFilter filter;
                            try {
                                filter = resolveFilter(new RestconfStream.SubscriptionFilter
                                    .SubtreeDefinition(subtree));
                            } catch (RequestException e) {
                                // what should happen?
                                throw new RuntimeException(e);
                            }
                            putFilter(name ,filter);
                        });
                        // final var name = (String) data.childByArg(FILTER_NAME).body();
                        // extractFilter() from data?
                        //final var filterSpec = (ChoiceNode) data.childByArg(new NodeIdentifier(FilterSpec.QNAME));

                        // FIXME: finish xpath
//                        final var xpath = (String) filterSpec.childByArg(new NodeIdentifier
//                            (QName.create(FilterSpec.QNAME, "stream-xpath-filter"))).body();
                    }
                    case DELETE, DISAPPEARED -> {
                        final var data = (MapNode) change.getRootNode().dataBefore();
                        data.body().forEach(entry -> {
                            final var name = (String) entry.childByArg(FILTER_NAME).body();
                            removeFilter(name);
                        });
                    }
                    // FIXME: what to do here?
                    case UNMODIFIED ->
                        // no reason to do anything with an unmodified node
                        LOG.debug("DataTreeCandidate for a filter is unmodified, nothing to change. Candidate: {}",
                            node);
                    default -> throw new IllegalStateException("Unexpected value: " + node.modificationType());
                }
            }
        }

        @Override
        public void onInitialData() {
            // TODO: finish?
            //  try to load filters from dataBroker?
        }
    }

    @Inject
    @Activate
    public MdsalRestconfStreamRegistry(@Reference final DOMDataBroker dataBroker,
            @Reference final RestconfStream.LocationProvider locationProvider) {
        this.dataBroker = requireNonNull(dataBroker);
        final var changeService = dataBroker.extension(DOMDataBroker.DataTreeChangeExtension.class);
        if (changeService != null) {
            changeService.registerTreeChangeListener(DOMDataTreeIdentifier.of(LogicalDatastoreType.CONFIGURATION,
                FILTERS), new FilterDataTreeChangeListener());
        }
        supports = List.of(new Rfc8639StreamSupport(), new Rfc8040StreamSupport(locationProvider));
    }

    @Override
    protected ListenableFuture<Void> putStream(final RestconfStream<?> stream, final String description,
            final URI restconfURI) {
        // Now issue a put operation
        final var tx = dataBroker.newWriteOnlyTransaction();
        for (var support : supports) {
            support.putStream(tx, stream, description, restconfURI);
        }
        return tx.commit().transform(unused -> null, MoreExecutors.directExecutor());
    }

    @Override
    protected ListenableFuture<Void> deleteStream(final String streamName) {
        // Now issue a delete operation while the name is still protected by being associated in the map.
        final var tx = dataBroker.newWriteOnlyTransaction();
        for (var support : supports) {
            support.deleteStream(tx, streamName);
        }
        return tx.commit().transform(unused -> null, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Void> updateReceiver(final ReceiverHolder receiver, final long counter,
            final ReceiverHolder.RecordType recordType) {
        // Now issue a merge operation
        final var tx = dataBroker.newWriteOnlyTransaction();
        final var subscriptionId = receiver.subscriptionId();
        final var sentEventIid = YangInstanceIdentifier.builder()
            .node(NodeIdentifier.create(Subscriptions.QNAME))
            .node(NodeIdentifier.create(Subscription.QNAME))
            .node(NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_ID, Uint32.valueOf(subscriptionId)))
            .node(NodeIdentifier.create(Receivers.QNAME))
            .node(NodeIdentifier.create(Receiver.QNAME))
            .node(NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_RECEIVER_NAME,
                receiver.receiverName()));

        final LeafNode<Uint64> counterValue;
        switch (recordType) {
            case SENT_EVENT_RECORDS -> {
                sentEventIid.node(NodeIdentifier.create(QNAME_SENT_EVENT_RECORDS));
                counterValue = ImmutableNodes.leafNode(
                    QNAME_SENT_EVENT_RECORDS, Uint64.valueOf(receiver.sentEventCounter().get()));
            }
            case EXCLUDED_EVENT_RECORDS -> {
                sentEventIid.node(NodeIdentifier.create(QNAME_EXCLUDED_EVENT_RECORDS));
                counterValue = ImmutableNodes.leafNode(
                    QNAME_EXCLUDED_EVENT_RECORDS, Uint64.valueOf(counter));
            }
            default -> throw new IllegalArgumentException("Unknown record type: " + recordType);
        }

        tx.merge(LogicalDatastoreType.OPERATIONAL, sentEventIid.build(), counterValue);
        return tx.commit().transform(unused -> null, MoreExecutors.directExecutor());
    }

    @Override
    protected ListenableFuture<RestconfStream.Subscription> createSubscription(
            final RestconfStream.Subscription subscription) {
        final var id = subscription.id();
        final var receiver = subscription.receiverName();
        final var nodeId = NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_ID, id);

        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, SubscriptionUtil.SUBSCRIPTIONS.node(nodeId),
            ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(nodeId)
                .withChild(ImmutableNodes.leafNode(QNAME_ID, id))
                .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ENCODING, subscription.encoding()))
                .withChild(ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(SubscriptionUtil.QNAME_TARGET))
                    .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, subscription.streamName()))
//                    .withChild(ImmutableNodes.newChoiceBuilder()
//                        .withNodeIdentifier(NodeIdentifier.create(StreamFilter.QNAME))
//                        .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM_FILTER,
//                            subscription.filterName()))
//                        .build())
                    .build())
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(Receivers.QNAME))
                    .withChild(ImmutableNodes.newSystemMapBuilder()
                        .withNodeIdentifier(NodeIdentifier.create(Receiver.QNAME))
                        .withChild(ImmutableNodes.newMapEntryBuilder()
                            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME,
                                QNAME_RECEIVER_NAME, receiver))
                            .withChild(ImmutableNodes.leafNode(QNAME_RECEIVER_NAME, receiver))
                            .withChild(ImmutableNodes.leafNode(QNAME_SENT_EVENT_RECORDS, Uint64.ZERO))
                            .withChild(ImmutableNodes.leafNode(QNAME_EXCLUDED_EVENT_RECORDS, Uint64.ZERO))
                            .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_RECEIVER_STATE,
                                Receiver.State.Active.getName()))
                            .build())
                        .build())
                    .build())
                .build());
        return tx.commit().transform(info -> {
            LOG.debug("Added subscription {} to operational datastore as of {}", id, info);
            return new MdsalRestconfStreamSubscription<>(subscription, dataBroker);
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected ListenableFuture<RestconfStream.Subscription> modifySubscriptionFilter(
            final RestconfStream.Subscription subscription, final RestconfStream.SubscriptionFilter filter) {
        final var id = subscription.id();

        final DataContainerChild filterNode = switch (filter) {
            case RestconfStream.SubscriptionFilter.Reference(var filterName) ->
                ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM_FILTER, filterName);
            case RestconfStream.SubscriptionFilter.SubtreeDefinition(var anydata) ->
                ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(FilterSpec.QNAME))
                    .withChild(ImmutableNodes.leafNode(StreamSubtreeFilter.QNAME, anydata))
                    .build();
            case RestconfStream.SubscriptionFilter.XPathDefinition(final var xpath) ->
                ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(FilterSpec.QNAME))
                    .withChild(ImmutableNodes.leafNode(QName.create(FilterSpec.QNAME, "stream-xpath-filter"), xpath))
                    .build();
        };

        final var tx = dataBroker.newWriteOnlyTransaction();
        final var nodeId = NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, id);
        tx.merge(LogicalDatastoreType.OPERATIONAL, SubscriptionUtil.SUBSCRIPTIONS.node(nodeId),
            ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(nodeId)
                .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, id))
                .withChild(ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(SubscriptionUtil.QNAME_TARGET))
                    .withChild(ImmutableNodes.newChoiceBuilder()
                        .withNodeIdentifier(NodeIdentifier.create(StreamFilter.QNAME))
                        .withChild(filterNode)
                        .build())
                    .build())
                .build());
        return tx.commit().transform(info -> {
            LOG.debug("Modified subscription {} to operational datastore as of {}", id, info);
            return new MdsalRestconfStreamSubscription<>(subscription, dataBroker);
        }, MoreExecutors.directExecutor());
    }
}
