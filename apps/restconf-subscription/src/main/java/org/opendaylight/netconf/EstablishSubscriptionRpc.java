/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Encoding;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.dynamic.Stream1Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.target.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.target.stream.stream.filter.ByReferenceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.SubscriptionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RESTCONF implementation of {@link EstablishSubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public class EstablishSubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER_NAME =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream-filter-name").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM_SUBTREE_FILTER =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream-subtree-filter").intern());
    private static final NodeIdentifier SUBSCRIPTION_STOP_TIME =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stop-time").intern());
    private static final NodeIdentifier SUBSCRIPTION_ENCODING =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "encoding").intern());
    private static final YangInstanceIdentifier SUBSCRIPTIONS = YangInstanceIdentifier.of(
        NodeIdentifier.create(Subscriptions.QNAME),
        NodeIdentifier.create(Subscription.QNAME));

    private static final QName QNAME_ID = QName.create(Subscription.QNAME, "id").intern();
    private static final QName QNAME_STRAM_FILTER = QName.create(Subscription.QNAME, "stream-filter-name").intern();
    private static final QName QNAME_STOP_TIME = QName.create(Subscription.QNAME, "stop-time").intern();
    private static final QName QNAME_ENCODING = QName.create(Subscription.QNAME, "encoding").intern();

    // Start subscription ID generation from the upper half of uint32 (2,147,483,648)
    private static final long INITIAL_SUBSCRIPTION_ID = 2147483648L;

    private final AtomicLong subscriptionIdCounter = new AtomicLong(INITIAL_SUBSCRIPTION_ID);
    private final DOMDataBroker dataBroker;

    @Inject
    @Activate
    public EstablishSubscriptionRpc(@Reference final DOMDataBroker dataBroker) {
        super(EstablishSubscription.QNAME);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {
        final var body = input.input();
        final var stream = leaf(body, SUBSCRIPTION_STREAM, String.class);
        final var streamFilter = leaf(body, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
        final var streamSubtree = leaf(body, SUBSCRIPTION_STREAM_SUBTREE_FILTER, String.class);
        final var stopTime = leaf(body, SUBSCRIPTION_STOP_TIME, DateAndTime.class);
        final var encoding = leaf(body, SUBSCRIPTION_ENCODING, Encoding.class);
        final var session = request.session();

        final var builder = new SubscriptionBuilder();
        final var streamBuilder = new StreamBuilder();

        if (stream != null) {
            final var stream1Builder = new Stream1Builder();
            stream1Builder.setStream(stream);
            streamBuilder.addAugmentation(stream1Builder.build());
        }
        if (streamFilter != null) {
            final var byReferenceBuilder = new ByReferenceBuilder();
            byReferenceBuilder.setStreamFilterName(streamFilter);
            streamBuilder.setStreamFilter(byReferenceBuilder.build());
        }
        if (streamSubtree != null) {
            //TODO: parse anydata filter, rfc6241? https://www.rfc-editor.org/rfc/rfc8650#name-filter-example
        }
        if (stopTime != null) {
            builder.setStopTime(stopTime);
        }
        if (encoding != null) {
            builder.setEncoding(encoding);
        }
        builder.setId(new SubscriptionId(Uint32.valueOf(
            SubscriptionUtil.generateSubscriptionId(subscriptionIdCounter))));

        final var subscription = new SubscriptionHolder(builder.build(), dataBroker);
        session.registerResource(subscription);

        // FIXME: move to plugins
        final var tx = dataBroker.newWriteOnlyTransaction();
        final var node = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_ID, builder.getId().getValue().longValue()))
            .withChild(ImmutableNodes.leafNode(QNAME_ID, builder.getId().getValue().longValue()))
            .withChild(ImmutableNodes.leafNode(QNAME_STRAM_FILTER, streamFilter))
            .withChild(ImmutableNodes.leafNode(QNAME_STOP_TIME, stopTime))
            .withChild(ImmutableNodes.leafNode(QNAME_ENCODING, encoding))
            .build();
        tx.put(LogicalDatastoreType.OPERATIONAL, SUBSCRIPTIONS.node(node.name()), node);
        tx.commit();
    }
}
