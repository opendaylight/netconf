/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Encoding;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.dynamic.Stream1Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.target.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.target.stream.stream.filter.ByReferenceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.SubscriptionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.Receivers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RESTCONF implementation of {@link EstablishSubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public final class EstablishSubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER_NAME =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream-filter-name").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream").intern());
    private static final NodeIdentifier SUBSCRIPTION_TARGET =
            NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "target").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream-filter").intern());
    private static final NodeIdentifier SUBSCRIPTION_STOP_TIME =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stop-time").intern());
    private static final NodeIdentifier SUBSCRIPTION_ENCODING =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "encoding").intern());
    private static final NodeIdentifier ESTABLISH_SUBSCRIPTION_OUTPUT =
        NodeIdentifier.create(EstablishSubscriptionOutput.QNAME);
    private static final NodeIdentifier OUTPUT_ID =
        NodeIdentifier.create(QName.create(EstablishSubscriptionOutput.QNAME, "id").intern());

    // Start subscription ID generation from the upper half of uint32 (2,147,483,648)
    private static final long INITIAL_SUBSCRIPTION_ID = 2147483648L;

    private final AtomicLong subscriptionIdCounter = new AtomicLong(INITIAL_SUBSCRIPTION_ID);
    private final MdsalNotificationService mdsalService;
    private final SubscriptionStateService subscriptionStateService;
    private final SubscriptionStateMachine stateMachine;

    @Inject
    @Activate
    public EstablishSubscriptionRpc(@Reference final MdsalNotificationService mdsalService,
            @Reference final SubscriptionStateService subscriptionStateService,
            @Reference final SubscriptionStateMachine stateMachine) {
        super(EstablishSubscription.QNAME);
        this.mdsalService = requireNonNull(mdsalService);
        this.subscriptionStateService = requireNonNull(subscriptionStateService);
        this.stateMachine = requireNonNull(stateMachine);
    }

    @Override
    public void invoke(final ServerRequest<ContainerNode> request, final URI restconfURI, final OperationInput input) {
        final var session = request.session();
        if (session == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED,
                "This end point does not support dynamic subscriptions."));
            return;
        }

        final var body = input.input();
        final var target = (DataContainerNode) body.childByArg(SUBSCRIPTION_TARGET);
        if (target == null) {
            // means there is no stream information present
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No stream specified"));
            return;
        }

        final var id = Uint32.valueOf(SubscriptionUtil.generateSubscriptionId(subscriptionIdCounter));

        final var subscriptionBuilder = new SubscriptionBuilder();
        subscriptionBuilder.setId(new SubscriptionId(id));
        final var streamBuilder = new StreamBuilder();

        final var nodeBuilder = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, id))
            .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, id));
        final var nodeTargetBuilder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
            .create(SubscriptionUtil.QNAME_TARGET));

        final var principal = request.principal();
        nodeBuilder.withChild(generateReceivers(principal == null ? "unknown" : principal.getName()));

        // check stream name
        final var streamName = leaf(target, SUBSCRIPTION_STREAM, String.class);
        if (streamName == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No stream specified"));
            return;
        }
        try {
            if (!mdsalService.exist(SubscriptionUtil.STREAMS.node(NodeIdentifierWithPredicates.of(Stream.QNAME,
                    SubscriptionUtil.QNAME_STREAM_NAME, streamName))).get()) {
                request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                    "%s refers to an unknown stream", streamName));
                return;
            }
        } catch (InterruptedException | ExecutionException e) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
            return;
        }
        final var stream1Builder = new Stream1Builder();
        stream1Builder.setStream(streamName);
        streamBuilder.addAugmentation(stream1Builder.build());
        nodeTargetBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, streamName));

        // check stream filter
        final var streamFilter = (DataContainerNode) target.childByArg(SUBSCRIPTION_STREAM_FILTER);
        if (streamFilter != null) {
            final var streamFilterName = leaf(streamFilter, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
            final var nodeFilterBuilder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
                .create(StreamFilter.QNAME));
            if (streamFilterName != null) {
                try {
                    if (!mdsalService.exist(SubscriptionUtil.FILTERS.node(NodeIdentifierWithPredicates.of(
                            StreamFilter.QNAME, SubscriptionUtil.QNAME_STREAM_FILTER_NAME, streamFilterName))).get()) {
                        request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                            "%s refers to an unknown stream filter", streamFilterName));
                        return;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
                    return;
                }
                final var byReferenceBuilder = new ByReferenceBuilder();
                byReferenceBuilder.setStreamFilterName(streamFilterName);
                streamBuilder.setStreamFilter(byReferenceBuilder.build());
                nodeFilterBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM_FILTER,
                    streamFilterName));
                nodeTargetBuilder.withChild(nodeFilterBuilder.build());
            }
            //  TODO: parse anydata filter, rfc6241? https://www.rfc-editor.org/rfc/rfc8650#name-filter-example
            //    {@link StreamSubtreeFilter}.
        }
        nodeBuilder.withChild(nodeTargetBuilder.build());

        final DateAndTime stopTime;
        try {
            stopTime = leaf(body, SUBSCRIPTION_STOP_TIME, DateAndTime.class);
        } catch (IllegalArgumentException e) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
            return;
        }
        if (stopTime != null) {
            subscriptionBuilder.setStopTime(stopTime);
            nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STOP_TIME, stopTime));
        }

        final var encoding = leaf(body, SUBSCRIPTION_ENCODING, Encoding.class);
        if (encoding != null) {
            subscriptionBuilder.setEncoding(encoding);
            nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ENCODING, encoding));
        }

        final var subscription = new SubscriptionHolder(subscriptionBuilder.build(), mdsalService,
            subscriptionStateService, stateMachine);
        session.registerResource(subscription);
        final var node = nodeBuilder.build();
        stateMachine.registerSubscription(session, id);

        mdsalService.writeSubscription(SubscriptionUtil.SUBSCRIPTIONS.node(node.name()), node)
            .addCallback(new FutureCallback<CommitInfo>() {
                @Override
                public void onSuccess(final CommitInfo result) {
                    stateMachine.moveTo(id, SubscriptionState.ACTIVE);
                    request.completeWith(ImmutableNodes.newContainerBuilder()
                        .withNodeIdentifier(ESTABLISH_SUBSCRIPTION_OUTPUT)
                        .withChild(ImmutableNodes.leafNode(OUTPUT_ID, id))
                        .build());
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                        throwable.getCause().getMessage()));
                }
            }, MoreExecutors.directExecutor());
    }

    private static ContainerNode generateReceivers(final String receiver) {
        return ImmutableNodes.newContainerBuilder().withNodeIdentifier(NodeIdentifier
            .create(Receivers.QNAME))
                .withChild(ImmutableNodes.newSystemMapBuilder()
                        .withNodeIdentifier(NodeIdentifier.create(Receiver.QNAME))
                    .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME,
                        SubscriptionUtil.QNAME_RECEIVER_NAME, receiver))
                    .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_RECEIVER_NAME, receiver))
                    .withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_RECEIVER_STATE,
                        Receiver.State.Active.getName()))
                    .build())
                .build())
            .build();
    }
}
