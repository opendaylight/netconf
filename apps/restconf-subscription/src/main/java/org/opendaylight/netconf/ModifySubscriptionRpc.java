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
    //private static final NodeIdentifier SUBSCRIPTION_STREAM_SUBTREE_FILTER =
    //    NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stream-subtree-filter").intern());
    private static final NodeIdentifier SUBSCRIPTION_STOP_TIME =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stop-time").intern());

    private static final Logger LOG = LoggerFactory.getLogger(ModifySubscriptionRpc.class);

    private final MdsalNotificationService mdsalService;
    private final SubscriptionStateService SubscriptionStateService;

    @Inject
    @Activate
    public ModifySubscriptionRpc(@Reference final MdsalNotificationService mdsalService,
            @Reference final SubscriptionStateService SubscriptionStateService) {
        super(ModifySubscription.QNAME);
        this.mdsalService = requireNonNull(mdsalService);
        this.SubscriptionStateService = requireNonNull(SubscriptionStateService);
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {
        final var body = input.input();
        final Uint32 id;
        final DateAndTime stopTime;
        final var target = (DataContainerNode) body.childByArg(SUBSCRIPTION_TARGET);
        final var streamFilter = (DataContainerNode) target.childByArg(SUBSCRIPTION_STREAM_FILTER);
        final var streamFilterName = leaf(streamFilter, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
        //final var streamSubtree = leaf(streamFilter, SUBSCRIPTION_STREAM_SUBTREE_FILTER, String.class);

        final var nodeBuilder = ImmutableNodes.newMapEntryBuilder();
        final var nodeTargetBuilder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
            .create(QName.create(Subscription.QNAME, "target").intern()));
        final var nodeFilterBuilder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(NodeIdentifier
            .create(QName.create(Subscription.QNAME, "stream-filter").intern()));

        try {
            id = leaf(body, SUBSCRIPTION_ID, Uint32.class);
        } catch (IllegalArgumentException e) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
            return;
        }
        if (id == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No id specified"));
            return;
        }
        try {
            stopTime = leaf(body, SUBSCRIPTION_STOP_TIME, DateAndTime.class);
        } catch (IllegalArgumentException e) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
            return;
        }
        //if (streamSubtree != null) {
            //TODO: parse anydata filter, rfc6241? https://www.rfc-editor.org/rfc/rfc8650#name-filter-example
            // {@link StreamSubtreeFilter}.
        //}

        nodeBuilder.withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME,
            SubscriptionUtil.QNAME_ID, id));
        nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_ID, id));
        if (streamFilterName != null) {
            try {
                if (!mdsalService.read(SubscriptionUtil.FILTERS.node(NodeIdentifierWithPredicates.of(StreamFilter.QNAME,
                    SubscriptionUtil.QNAME_STREAM_FILTER_NAME, streamFilterName))).get()) {
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
        if (stopTime != null) {
            nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STOP_TIME, stopTime));
        }
        final var node = nodeBuilder.build();

        //FIXME: this is not correct way to verify if subscription with given id exist on given session
        for (var resource : request.session().getResources()) {
            if (resource instanceof SubscriptionHolder subscription) {
                if (subscription.subscription().getId().getValue().equals(id)) {
                    mdsalService.mergeSubscription(SubscriptionUtil.SUBSCRIPTIONS.node(node.name()), node)
                        .addCallback(new FutureCallback<CommitInfo>() {
                            @Override
                            public void onSuccess(CommitInfo result) {
                                request.completeWith(ImmutableNodes.newContainerBuilder()
                                    .withNodeIdentifier(NodeIdentifier.create(ModifySubscriptionOutput.QNAME))
                                    .build());
                                try {
                                    SubscriptionStateService.subscriptionModified(Instant.now().toString(),
                                        id.longValue(), "uri", streamFilterName);
                                } catch (InterruptedException e) {
                                    LOG.info("Could not send subscription modify notification: {}", e.getMessage());
                                }
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                request.completeWith(new ServerException(ErrorType.APPLICATION,
                                    ErrorTag.OPERATION_FAILED, throwable.getCause().getMessage()));
                            }
                        }, MoreExecutors.directExecutor());
                    return;
                }
            }
        }
        request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
            "Subscription with given id does not exist on this session"));
    }
}
