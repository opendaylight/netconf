/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBroker.DataTreeChangeExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeListener;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.databind.DatabindProvider;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.netconf.databind.subtree.SubtreeFilter;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.NormalizedNodeWriter;
import org.opendaylight.restconf.server.spi.ReceiverHolder;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.SubtreeEventStreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Filters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamXpathFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.Target;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.Receivers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This singleton class is responsible for creation, removal and searching for {@link RestconfStream}s.
 */
@Singleton
@Component(service = RestconfStream.Registry.class)
public final class MdsalRestconfStreamRegistry extends AbstractRestconfStreamRegistry implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfStreamRegistry.class);

    private static final QName ID_QNAME = QName.create(Subscription.QNAME, "id").intern();
    private static final QName NAME_QNAME = QName.create(StreamFilter.QNAME, "name").intern();

    private static final NodeIdentifier ENCODING_NODEID =
        NodeIdentifier.create(QName.create(Subscription.QNAME, "encoding").intern());
    private static final NodeIdentifier EXCLUDED_EVENT_RECORDS_NODEID =
        NodeIdentifier.create(QName.create(Receiver.QNAME, "excluded-event-records"));
    private static final NodeIdentifier FILTERS_NODEID = NodeIdentifier.create(Filters.QNAME);
    private static final NodeIdentifier FILTER_SPEC_NODEID = NodeIdentifier.create(FilterSpec.QNAME);
    private static final NodeIdentifier ID_NODEID = NodeIdentifier.create(ID_QNAME);
    private static final NodeIdentifier NAME_NODEID = NodeIdentifier.create(NAME_QNAME);
    private static final NodeIdentifier RECEIVER_NODEID = NodeIdentifier.create(Receiver.QNAME);
    private static final NodeIdentifier RECEIVERS_NODEID = NodeIdentifier.create(Receivers.QNAME);
    private static final NodeIdentifier SENT_EVENT_RECORDS_NODEID =
        NodeIdentifier.create(QName.create(Receiver.QNAME, "sent-event-records").intern());
    private static final NodeIdentifier SUBSCRIPTION_NODEID = NodeIdentifier.create(Subscription.QNAME);
    private static final NodeIdentifier SUBSCRIPTIONS_NODEID = NodeIdentifier.create(Subscriptions.QNAME);
    private static final NodeIdentifier STATE_NODEID =
        NodeIdentifier.create(QName.create(Receiver.QNAME, "state").intern());
    private static final NodeIdentifier STREAM_NODEID = NodeIdentifier.create(Stream.QNAME);
    private static final NodeIdentifier STREAM_FILTER_NODEID = NodeIdentifier.create(StreamFilter.QNAME);
    private static final NodeIdentifier STREAM_FILTER_NAME_NODEID =
        NodeIdentifier.create(QName.create(Subscription.QNAME, "stream-filter-name").intern());
    private static final NodeIdentifier STREAM_XPATH_FILTER_NODEID = NodeIdentifier.create(StreamXpathFilter.QNAME);
    private static final NodeIdentifier STREAM_SUBTREE_FILTER_NODEID = NodeIdentifier.create(StreamSubtreeFilter.QNAME);
    private static final NodeIdentifier TARGET_NODEID = NodeIdentifier.create(Target.QNAME);

    private final DOMDataBroker dataBroker;
    private final DOMNotificationService notificationService;
    private final DatabindProvider databindProvider;
    private final List<StreamSupport> supports;
    private final Registration sclReg;
    private final Registration tclReg;

    private DefaultNotificationSource notificationSource;

    @Inject
    @Activate
    public MdsalRestconfStreamRegistry(@Reference final DOMDataBroker dataBroker,
            @Reference final DOMNotificationService notificationService,
            @Reference final DOMSchemaService schemaService,
            @Reference final RestconfStream.LocationProvider locationProvider,
            @Reference final DatabindProvider databindProvider) {
        this.dataBroker = requireNonNull(dataBroker);
        this.notificationService = requireNonNull(notificationService);
        this.databindProvider = requireNonNull(databindProvider);
        supports = List.of(new Rfc8639StreamSupport(), new Rfc8040StreamSupport(locationProvider));

        // FIXME: the source should be handling its own updates and we should only call start() once
        notificationSource = new DefaultNotificationSource(notificationService, schemaService.getGlobalContext());
        start(notificationSource);
        sclReg = schemaService.registerSchemaContextListener(this::onModelContextUpdated);

        final var changeExtension = dataBroker.extension(DataTreeChangeExtension.class);
        if (changeExtension != null) {
            tclReg = changeExtension.registerTreeChangeListener(
                DOMDataTreeIdentifier.of(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(
                    FILTERS_NODEID,
                    STREAM_FILTER_NODEID,
                    // wildcard: any filter
                    STREAM_FILTER_NODEID,
                    FILTER_SPEC_NODEID)),
                new DOMDataTreeChangeListener() {
                    @Override
                    public void onInitialData() {
                        // No filters at all
                    }

                    @Override
                    public void onDataTreeChanged(final List<DataTreeCandidate> changes) {
                        onFilterSpecsUpdated(changes);
                    }
                });
        } else {
            tclReg = null;
        }
    }

    @PreDestroy
    @Deactivate
    @Override
    public synchronized void close() {
        if (tclReg != null) {
            tclReg.close();
        }
        sclReg.close();
        if (notificationSource != null) {
            notificationSource.close();
        }
    }

    private synchronized void onModelContextUpdated(final EffectiveModelContext context) {
        if (notificationSource != null) {
            notificationSource.close();
        }
        notificationSource = new DefaultNotificationSource(notificationService, context);
        start(notificationSource);
    }

    @NonNullByDefault
    private void onFilterSpecsUpdated(final List<DataTreeCandidate> changes) {
        final var nameToSpec = new HashMap<String, @Nullable ChoiceNode>();

        for (var change : changes) {
            // candidate root points to the the filter-spec itself, parent path identifies the filter that changed
            final var filterId = verifyNotNull((NodeIdentifierWithPredicates)
                change.getRootPath().coerceParent().getLastPathArgument());
            final var filterName = verifyNotNull((String) filterId.getValue(NAME_QNAME));

            final var node = change.getRootNode();
            switch (node.modificationType()) {
                case null -> throw new NullPointerException("Modification type is null for node: " + node);
                case APPEARED, SUBTREE_MODIFIED, WRITE ->
                    nameToSpec.put(filterName, (ChoiceNode) node.getDataBefore());
                case DELETE, DISAPPEARED -> nameToSpec.put(filterName, null);
                case UNMODIFIED -> {
                    // No-op
                }
            }
        }

        updateFilterDefinitions(nameToSpec);
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
        final var counterLeaf = switch (recordType) {
            case SENT_EVENT_RECORDS ->
                ImmutableNodes.leafNode(SENT_EVENT_RECORDS_NODEID, Uint64.valueOf(receiver.sentEventCounter().get()));
            case EXCLUDED_EVENT_RECORDS ->
                ImmutableNodes.leafNode(EXCLUDED_EVENT_RECORDS_NODEID, Uint64.valueOf(counter));
        };

        // Now issue a merge operation
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.merge(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of(
            SUBSCRIPTIONS_NODEID,
            SUBSCRIPTION_NODEID,
            subscriptionArg(receiver.subscriptionId()),
            RECEIVERS_NODEID,
            RECEIVER_NODEID,
            receiverArg(receiver.receiverName()),
            counterLeaf.name()), counterLeaf);
        return tx.commit().transform(unused -> null, MoreExecutors.directExecutor());
    }

    @Override
    protected ListenableFuture<RestconfStream.Subscription> createSubscription(
            final RestconfStream.Subscription subscription) {
        final var id = subscription.id();
        final var receiverName = subscription.receiverName();
        final var pathArg = subscriptionArg(id);

        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, subscriptionPath(pathArg),
            ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(pathArg)
                .withChild(ImmutableNodes.leafNode(ID_NODEID, id))
                .withChild(ImmutableNodes.leafNode(ENCODING_NODEID, subscription.encoding()))
                .withChild(ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(TARGET_NODEID)
                    .withChild(ImmutableNodes.leafNode(STREAM_NODEID, subscription.streamName()))
//                    .withChild(ImmutableNodes.newChoiceBuilder()
//                        .withNodeIdentifier(STREAM_FILTER_NODEID)
//                        .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM_FILTER,
//                            subscription.filterName()))
//                        .build())
                    .build())
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(RECEIVERS_NODEID)
                    .withChild(ImmutableNodes.newSystemMapBuilder()
                        .withNodeIdentifier(RECEIVER_NODEID)
                        .withChild(ImmutableNodes.newMapEntryBuilder()
                            .withNodeIdentifier(receiverArg(receiverName))
                            .withChild(ImmutableNodes.leafNode(NAME_NODEID, receiverName))
                            .withChild(ImmutableNodes.leafNode(SENT_EVENT_RECORDS_NODEID, Uint64.ZERO))
                            .withChild(ImmutableNodes.leafNode(EXCLUDED_EVENT_RECORDS_NODEID, Uint64.ZERO))
                            .withChild(ImmutableNodes.leafNode(STATE_NODEID, Receiver.State.Active.getName()))
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

        final var filterNode = switch (filter) {
            case RestconfStream.SubscriptionFilter.Reference(var filterName) ->
                ImmutableNodes.leafNode(STREAM_FILTER_NAME_NODEID, filterName);
            case RestconfStream.SubscriptionFilter.SubtreeDefinition(var anydata) ->
                ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(FILTER_SPEC_NODEID)
                    .withChild(ImmutableNodes.leafNode(STREAM_SUBTREE_FILTER_NODEID, anydata))
                    .build();
            case RestconfStream.SubscriptionFilter.XPathDefinition(final var xpath) ->
                ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(FILTER_SPEC_NODEID)
                    .withChild(ImmutableNodes.leafNode(STREAM_XPATH_FILTER_NODEID, xpath))
                    .build();
        };

        final var pathArg = subscriptionArg(id);
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.merge(LogicalDatastoreType.OPERATIONAL, subscriptionPath(pathArg),
            ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(pathArg)
                .withChild(ImmutableNodes.leafNode(ID_NODEID, id))
                .withChild(ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(TARGET_NODEID)
                    .withChild(ImmutableNodes.newChoiceBuilder()
                        .withNodeIdentifier(STREAM_FILTER_NODEID)
                        .withChild(filterNode)
                        .build())
                    .build())
                .build());
        return tx.commit().transform(info -> {
            LOG.debug("Modified subscription {} to operational datastore as of {}", id, info);
            return new MdsalRestconfStreamSubscription<>(subscription, dataBroker);
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected EventStreamFilter parseSubtreeFilter(final AnydataNode<?> filter) throws RequestException {
        final SubtreeFilter databindFilter;
        try {
            final var databindContext = databindProvider.currentDatabind();
            final var writer = new StringWriter();
            final var xmlStreamWriter = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(writer);

            final var xmlNormalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlStreamWriter,
                databindContext.modelContext(), YangInstanceIdentifier.of(FILTERS_NODEID, STREAM_FILTER_NODEID));
            final var normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(xmlNormalizedNodeStreamWriter, null);
            normalizedNodeWriter.write(filter);
            normalizedNodeWriter.flush();

            databindFilter = SubtreeFilter.readFrom(databindContext, XMLInputFactory.newDefaultFactory()
                .createXMLStreamReader(new ByteArrayInputStream(writer.toString().getBytes(StandardCharsets.UTF_8))));
        } catch (IOException | XMLStreamException e) {
            LOG.debug("Failed to parse anydata to subtree filter {}", filter.prettyTree(), e);
            throw new RequestException("Failed to parse subtree filter", e);
        }
        return new SubtreeEventStreamFilter(databindFilter);
    }

    @NonNullByDefault
    private static NodeIdentifierWithPredicates receiverArg(final String receiverName) {
        return NodeIdentifierWithPredicates.of(Receiver.QNAME, NAME_QNAME, receiverName);
    }

    @NonNullByDefault
    static YangInstanceIdentifier subscriptionPath(final Uint32 subscriptionId) {
        return subscriptionPath(subscriptionArg(subscriptionId));
    }

    @NonNullByDefault
    private static YangInstanceIdentifier subscriptionPath(final NodeIdentifierWithPredicates pathArg) {
        return YangInstanceIdentifier.of(SUBSCRIPTIONS_NODEID, SUBSCRIPTION_NODEID, pathArg);
    }

    @NonNullByDefault
    private static NodeIdentifierWithPredicates subscriptionArg(final Uint32 subscriptionId) {
        return NodeIdentifierWithPredicates.of(Subscription.QNAME, ID_QNAME, subscriptionId);
    }
}
