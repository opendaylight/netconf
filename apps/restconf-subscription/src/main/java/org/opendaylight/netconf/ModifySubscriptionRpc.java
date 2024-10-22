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
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.restconf.notification.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
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
 * RESTCONF implementation of {@link ModifySubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public class ModifySubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier SUBSCRIPTION_ID =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "id").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER_NAME =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stream-filter-name").intern());
    //private static final NodeIdentifier SUBSCRIPTION_STREAM_SUBTREE_FILTER =
    //    NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stream-subtree-filter").intern());
    private static final NodeIdentifier SUBSCRIPTION_STOP_TIME =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stop-time").intern());

    private final MdsalNotificationService mdsalService;

    @Inject
    @Activate
    public ModifySubscriptionRpc(@Reference final MdsalNotificationService mdsalService) {
        super(EstablishSubscription.QNAME);
        this.mdsalService = requireNonNull(mdsalService);
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {
        final var body = input.input();
        final Uint32 id;
        final DateAndTime stopTime;
        final var streamFilter = leaf(body, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
        //final var streamSubtree = leaf(body, SUBSCRIPTION_STREAM_SUBTREE_FILTER, String.class);

        final var nodeBuilder = ImmutableNodes.newMapEntryBuilder();

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
        if (streamFilter != null) {
            try {
                if (!mdsalService.read(SubscriptionUtil.FILTERS.node(NodeIdentifierWithPredicates.of(StreamFilter.QNAME,
                    SubscriptionUtil.QNAME_STREAM_FILTER_NAME, streamFilter))).get()) {
                    request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                        "%s refers to an unknown stream filter", streamFilter));
                    return;
                }
            } catch (InterruptedException | ExecutionException e) {
                request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
                return;
            }
            nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STRAM_FILTER, streamFilter));
        }
        if (stopTime != null) {
            nodeBuilder.withChild(ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STOP_TIME, stopTime));
        }
        final var node = nodeBuilder.build();

        for (var resource : request.session().getResources()) {
            if (resource instanceof SubscriptionHolder subscription) {
                if (subscription.subscription().getId().getValue().equals(id)) {
                    mdsalService.mergeSubscription(SubscriptionUtil.SUBSCRIPTIONS.node(node.name()), node)
                        .addCallback(new FutureCallback<CommitInfo>() {
                            @Override
                            public void onSuccess(CommitInfo result) {
                                request.completeWith(ImmutableNodes.newContainerBuilder().build());
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                request.completeWith(new ServerException(ErrorType.APPLICATION,
                                    ErrorTag.OPERATION_FAILED, throwable.getMessage()));
                            }
                        }, MoreExecutors.directExecutor());
                }
            }
        }
        request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
            "Subscription with given id does not exist on this session"));
    }
}
