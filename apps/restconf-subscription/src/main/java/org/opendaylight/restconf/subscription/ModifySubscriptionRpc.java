/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTCONF implementation of {@link ModifySubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public final class ModifySubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier SUBSCRIPTION_ID =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "id").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER_NAME =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stream-filter-name").intern());
    private static final NodeIdentifier SUBSCRIPTION_TARGET =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "target").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stream-filter").intern());

    private static final Logger LOG = LoggerFactory.getLogger(ModifySubscriptionRpc.class);

    private final SubscriptionStateService subscriptionStateService;
    private final SubscriptionStateMachine stateMachine;
    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public ModifySubscriptionRpc(@Reference final RestconfStream.Registry streamRegistry,
            @Reference final SubscriptionStateService subscriptionStateService,
            @Reference final SubscriptionStateMachine stateMachine) {
        super(ModifySubscription.QNAME);
        this.subscriptionStateService = requireNonNull(subscriptionStateService);
        this.stateMachine = requireNonNull(stateMachine);
        this.streamRegistry = requireNonNull(streamRegistry);
    }

    @Override
    public void invoke(final ServerRequest<ContainerNode> request, final URI restconfURI, final OperationInput input) {
        final var body = input.input();
        final Uint32 id;
        final String streamFilterName;

        try {
            id = leaf(body, SUBSCRIPTION_ID, Uint32.class);
        } catch (IllegalArgumentException e) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
            return;
        }
        if (id == null) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No ID specified."));
            return;
        }

        final var state = stateMachine.lookupSubscriptionState(id);
        if (state == null) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No subscription with given ID."));
            return;
        }
        if (state != SubscriptionState.ACTIVE && state != SubscriptionState.SUSPENDED) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "There is no active or suspended subscription with given ID."));
            return;
        }

        if (stateMachine.lookupSubscriptionSession(id) != request.session()) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "Subscription with given id does not exist on this session"));
            return;
        }

        final var target = (DataContainerNode) body.childByArg(SUBSCRIPTION_TARGET);
        final var nodeBuilder = ImmutableNodes.newMapEntryBuilder();
        final var nodeTargetBuilder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
            .create(SubscriptionUtil.QNAME_TARGET));
        final var nodeFilterBuilder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
            .create(QName.create(Subscription.QNAME, "stream-filter")));

        nodeBuilder.withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME,
            SubscriptionUtil.QNAME_ID, id));
        nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, id));
        if (target != null) {
            final var streamFilter = (DataContainerNode) target.childByArg(SUBSCRIPTION_STREAM_FILTER);
            streamFilterName = leaf(streamFilter, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
            //  TODO: parse anydata filter, rfc6241? https://www.rfc-editor.org/rfc/rfc8650#name-filter-example
            //    {@link StreamSubtreeFilter}.
            if (streamFilterName != null) {
//                try {
//                    if (!mdsalService.exist(SubscriptionUtil.FILTERS.node(NodeIdentifierWithPredicates.of(
//                        StreamFilter.QNAME, SubscriptionUtil.QNAME_STREAM_FILTER_NAME, streamFilterName))).get()) {
//                        request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
//                            "%s refers to an unknown stream filter", streamFilterName));
//                        return;
//                    }
//                } catch (InterruptedException | ExecutionException e) {
//                    request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
//                    return;
//                }
                nodeFilterBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM_FILTER,
                    streamFilterName));
                nodeTargetBuilder.withChild(nodeFilterBuilder.build());
                nodeBuilder.withChild(nodeTargetBuilder.build());
            }
        }
//        final var node = nodeBuilder.build();

        request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED,
            "Not implemented yet"));

// FIXME: reconcile
//        mdsalService.mergeSubscription(SubscriptionUtil.SUBSCRIPTIONS.node(node.name()), node)
//            .addCallback(new FutureCallback<CommitInfo>() {
//                @Override
//                public void onSuccess(final CommitInfo result) {
//                    request.completeWith(ImmutableNodes.newContainerBuilder()
//                        .withNodeIdentifier(NodeIdentifier.create(ModifySubscriptionOutput.QNAME))
//                        .build());
//                    try {
//                        final var subscription = mdsalService.read(SubscriptionUtil.SUBSCRIPTIONS.node(node.name()))
//                            .get();
//                        if (subscription.isEmpty()) {
//                            LOG.warn("Could not send subscription modify notification: could not read stream name");
//                            return;
//                        }
//                        final var target = (DataContainerNode) ((DataContainerNode) subscription.orElseThrow())
//                            .childByArg(NodeIdentifier.create(SubscriptionUtil.QNAME_TARGET));
//                        final var streamName = leaf(target, NodeIdentifier.create(SubscriptionUtil.QNAME_STREAM),
//                            String.class);
//                        final var encoding = leaf((DataContainerNode) subscription.orElseThrow(),
//                            NodeIdentifier.create(SubscriptionUtil.QNAME_ENCODING), QName.class);
//                        // TODO: pass correct filter once we extract if from input
//                        subscriptionStateService.subscriptionModified(Instant.now(), id, streamName, encoding, null,
//                            stopTime, null);
//                    } catch (InterruptedException | ExecutionException e) {
//                        LOG.warn("Could not send subscription modify notification", e);
//                    }
//                }
//
//                @Override
//                public void onFailure(final Throwable throwable) {
//                    request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
//                        // FIXME: why getCause()?
//                        throwable.getCause()));
//                }
//            }, MoreExecutors.directExecutor());
    }
}
