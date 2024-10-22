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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.restconf.notification.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Encoding;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.dynamic.Stream1Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.target.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.target.stream.stream.filter.ByReferenceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.SubscriptionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
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
    //private static final NodeIdentifier SUBSCRIPTION_STREAM_SUBTREE_FILTER =
    //    NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream-subtree-filter").intern());
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
    private final RestconfStream.Registry streamRegistry;
    private final MdsalNotificationService mdsalService;

    @Inject
    @Activate
    public EstablishSubscriptionRpc(@Reference final RestconfStream.Registry streamRegistry,
            @Reference final MdsalNotificationService mdsalService) {
        super(EstablishSubscription.QNAME);
        this.streamRegistry = requireNonNull(streamRegistry);
        this.mdsalService = requireNonNull(mdsalService);
    }

    @Override
    public void invoke(final ServerRequest<ContainerNode> request, final URI restconfURI, final OperationInput input) {
        final var body = input.input();
        final var streamName = leaf(body, SUBSCRIPTION_STREAM, String.class);
        final var streamFilter = leaf(body, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
        //final var streamSubtree = leaf(body, SUBSCRIPTION_STREAM_SUBTREE_FILTER, String.class);
        final var stopTime = leaf(body, SUBSCRIPTION_STOP_TIME, DateAndTime.class);
        final var encoding = leaf(body, SUBSCRIPTION_ENCODING, Encoding.class);
        final var session = request.session();

        final var builder = new SubscriptionBuilder();
        final var streamBuilder = new StreamBuilder();
        final var nodeBuilder = ImmutableNodes.newMapEntryBuilder();

        final var id =  Uint32.valueOf(SubscriptionUtil.generateSubscriptionId(subscriptionIdCounter));
        builder.setId(new SubscriptionId(id));
        nodeBuilder.withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME,
            SubscriptionUtil.QNAME_ID, id));
        nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, id));

        if (streamName == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No stream specified"));
            return;
        }
        final var stream = streamRegistry.lookupStream(streamName);
        if (stream == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "%s refers to an unknown stream", streamName));
            return;
        }
        final var stream1Builder = new Stream1Builder();
        stream1Builder.setStream(streamName);
        streamBuilder.addAugmentation(stream1Builder.build());
        nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, streamName));
        if (streamFilter != null) {
            final var byReferenceBuilder = new ByReferenceBuilder();
            byReferenceBuilder.setStreamFilterName(streamFilter);
            streamBuilder.setStreamFilter(byReferenceBuilder.build());
            nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STRAM_FILTER, streamFilter));
        }
        //if (streamSubtree != null) {
            //TODO: parse anydata filter, rfc6241? https://www.rfc-editor.org/rfc/rfc8650#name-filter-example
            // {@link StreamSubtreeFilter}.
        //}
        if (stopTime != null) {
            builder.setStopTime(stopTime);
            nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STOP_TIME, stopTime));
        }
        if (encoding != null) {
            builder.setEncoding(encoding);
            nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ENCODING, encoding));
        }

        final var subscription = new SubscriptionHolder(builder.build(), mdsalService);
        session.registerResource(subscription);
        final var node = nodeBuilder.build();

        mdsalService.writeSubscription(SubscriptionUtil.SUBSCRIPTIONS.node(node.name()), node)
            .addCallback(new FutureCallback<CommitInfo>() {
                @Override
                public void onSuccess(CommitInfo result) {
                    request.completeWith(ImmutableNodes.newContainerBuilder()
                        .withNodeIdentifier(ESTABLISH_SUBSCRIPTION_OUTPUT)
                        .withChild(ImmutableNodes.leafNode(OUTPUT_ID, id))
                        //TODO: uri
                        .build());
                }

                @Override
                public void onFailure(Throwable throwable) {
                    request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                        throwable.getMessage()));
                }
            }, MoreExecutors.directExecutor());
    }
}
