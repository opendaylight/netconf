/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTCONF implementation of {@link ModifySubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public class ModifySubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier SUBSCRIPTION_ID =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "id").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER_NAME =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stream-filter-name").intern());
    private static final NodeIdentifier SUBSCRIPTION_TARGET =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "target").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stream-filter").intern());
    private static final NodeIdentifier SUBSCRIPTION_STOP_TIME =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stop-time").intern());

    private static final Logger LOG = LoggerFactory.getLogger(ModifySubscriptionRpc.class);

    private final MdsalNotificationService mdsalService;
    private final SubscriptionStateService subscriptionStateService;
    private final SubscriptionStateMachine stateMachine;

    @Inject
    @Activate
    public ModifySubscriptionRpc(@Reference final MdsalNotificationService mdsalService,
            @Reference final SubscriptionStateService subscriptionStateService,
            @Reference final SubscriptionStateMachine stateMachine) {
        super(ModifySubscription.QNAME);
        this.mdsalService = requireNonNull(mdsalService);
        this.subscriptionStateService = requireNonNull(subscriptionStateService);
        this.stateMachine = requireNonNull(stateMachine);
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {
        final var body = input.input();
        final Uint32 id;
        final DateAndTime stopTime;
        final String streamFilterName;

        try {
            id = leaf(body, SUBSCRIPTION_ID, Uint32.class);
        } catch (IllegalArgumentException e) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
            return;
        }
        if (id == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No ID specified."));
            return;
        }

        final var state = stateMachine.getSubscriptionState(id);
        if (state == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No subscription with given ID."));
            return;
        }
        if (state != SubscriptionState.ACTIVE && state != SubscriptionState.SUSPENDED) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "There is no active or suspended subscription with given ID."));
            return;
        }

        if (stateMachine.getSubscriptionSession(id) != request.session()) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "Subscription with given id does not exist on this session"));
            return;
        }

        final var target = (DataContainerNode) body.childByArg(SUBSCRIPTION_TARGET);
        final var nodeBuilder = ImmutableNodes.newMapEntryBuilder();
        final var nodeTargetBuilder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
            .create(SubscriptionUtil.QNAME_TARGET));
        final var nodeFilterBuilder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
            .create(QName.create(Subscription.QNAME, "stream-filter")));

        try {
            stopTime = leaf(body, SUBSCRIPTION_STOP_TIME, DateAndTime.class);
        } catch (IllegalArgumentException e) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
            return;
        }

        nodeBuilder.withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME,
            SubscriptionUtil.QNAME_ID, id));
        nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, id));
        if (target != null) {
            final var streamFilter = (DataContainerNode) target.childByArg(SUBSCRIPTION_STREAM_FILTER);
            streamFilterName = leaf(streamFilter, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
            //  TODO: parse anydata filter, rfc6241? https://www.rfc-editor.org/rfc/rfc8650#name-filter-example
            //    {@link StreamSubtreeFilter}.
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
                nodeFilterBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM_FILTER,
                    streamFilterName));
                nodeTargetBuilder.withChild(nodeFilterBuilder.build());
                nodeBuilder.withChild(nodeTargetBuilder.build());
            }
        }
        if (stopTime != null) {
            nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STOP_TIME, stopTime));
        }
        final var node = nodeBuilder.build();

        mdsalService.mergeSubscription(SubscriptionUtil.SUBSCRIPTIONS.node(node.name()), node)
            .addCallback(new FutureCallback<CommitInfo>() {
                @Override
                public void onSuccess(CommitInfo result) {
                    request.completeWith(ImmutableNodes.newContainerBuilder()
                        .withNodeIdentifier(NodeIdentifier.create(ModifySubscriptionOutput.QNAME))
                        .build());
                    try {
                        final var subscription = mdsalService.read(SubscriptionUtil.SUBSCRIPTIONS.node(node.name()))
                            .get();
                        if (subscription.isEmpty()) {
                            LOG.warn("Could not send subscription modify notification: could not read stream name");
                            return;
                        }
                        final var target = (DataContainerNode) ((DataContainerNode) subscription.orElseThrow())
                            .childByArg(NodeIdentifier.create(SubscriptionUtil.QNAME_TARGET));
                        final var streamName = leaf(target, NodeIdentifier.create(SubscriptionUtil.QNAME_STREAM),
                            String.class);
                        subscriptionStateService.subscriptionModified(Instant.now().toString(),
                            id.longValue(), streamName, "uri", null);
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.warn("Could not send subscription modify notification: {}", e.getMessage());
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    request.completeWith(new ServerException(ErrorType.APPLICATION,
                        ErrorTag.OPERATION_FAILED, throwable.getCause().getMessage()));
                }
            }, MoreExecutors.directExecutor());
    }
}
