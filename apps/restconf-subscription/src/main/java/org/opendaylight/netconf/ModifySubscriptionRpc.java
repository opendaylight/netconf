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
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.restconf.notification.mdsal.MdsalNotificationaService;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
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
    private static final NodeIdentifier SUBSCRIPTION_STREAM_SUBTREE_FILTER =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stream-subtree-filter").intern());
    private static final NodeIdentifier SUBSCRIPTION_STOP_TIME =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "stop-time").intern());
    private static final QName QNAME_ID = QName.create(Subscription.QNAME, "id").intern();
    private static final QName QNAME_STRAM_FILTER = QName.create(Subscription.QNAME, "stream-filter-name").intern();
    private static final QName QNAME_STOP_TIME = QName.create(Subscription.QNAME, "stop-time").intern();
    private static final YangInstanceIdentifier SUBSCRIPTIONS = YangInstanceIdentifier.of(
        NodeIdentifier.create(Subscriptions.QNAME),
        NodeIdentifier.create(Subscription.QNAME));

    private final MdsalNotificationaService mdsalService;

    @Inject
    @Activate
    public ModifySubscriptionRpc(@Reference final MdsalNotificationaService mdsalService) {
        super(EstablishSubscription.QNAME);
        this.mdsalService = requireNonNull(mdsalService);
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {
        final var body = input.input();
        final var id = leaf(body, SUBSCRIPTION_ID, Uint32.class);
        final var streamFilter = leaf(body, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
        final var streamSubtree = leaf(body, SUBSCRIPTION_STREAM_SUBTREE_FILTER, String.class);
        final var stopTime = leaf(body, SUBSCRIPTION_STOP_TIME, DateAndTime.class);

        if (streamSubtree != null) {
            //TODO: parse anydata filter, rfc6241? https://www.rfc-editor.org/rfc/rfc8650#name-filter-example
            // {@link StreamSubtreeFilter}.
        }

        //TODO: check id if exist
        final var node = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_ID, id))
            .withChild(ImmutableNodes.leafNode(QNAME_ID, id))
            .withChild(ImmutableNodes.leafNode(QNAME_STRAM_FILTER, streamFilter))
            .withChild(ImmutableNodes.leafNode(QNAME_STOP_TIME, stopTime))
            .build();

        mdsalService.mergeSubscription(SUBSCRIPTIONS.node(node.name()), node);
        //TODO: reply
    }
}
