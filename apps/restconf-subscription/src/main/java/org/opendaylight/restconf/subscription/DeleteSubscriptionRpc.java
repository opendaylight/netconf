/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.NoSuchSubscription;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTCONF implementation of {@link DeleteSubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
@NonNullByDefault
public final class DeleteSubscriptionRpc extends RpcImplementation {
    private static final Logger LOG = LoggerFactory.getLogger(DeleteSubscriptionRpc.class);

    private static final NodeIdentifier SUBSCRIPTION_ID =
        NodeIdentifier.create(QName.create(DeleteSubscriptionInput.QNAME, "id").intern());

    private final SubscriptionStateService subscriptionStateService;
    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public DeleteSubscriptionRpc(@Reference final RestconfStream.Registry streamRegistry,
            @Reference final SubscriptionStateService subscriptionStateService) {
        super(DeleteSubscription.QNAME);
        this.streamRegistry = requireNonNull(streamRegistry);
        this.subscriptionStateService = requireNonNull(subscriptionStateService);
    }

    @Override
    public void invoke(final ServerRequest<ContainerNode> request, final URI restconfURI, final OperationInput input) {
        final var body = input.input();
        final Uint32 id;
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
        final var subscription = streamRegistry.lookupSubscription(id);
        if (subscription == null) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No subscription with given ID."));
            return;
        }
        final var state = subscription.state();
        if (state != SubscriptionState.ACTIVE && state != SubscriptionState.SUSPENDED) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "There is no active or suspended subscription with given ID."));
            return;
        }

        if (subscription.session() != request.session()) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "Subscription with given id does not exist on this session"));
            return;
        }

        streamRegistry.updateSubscriptionState(subscription, SubscriptionState.END);
        try {
            subscriptionStateService.subscriptionTerminated(Instant.now(), id, NoSuchSubscription.QNAME)
                .addListener(() -> subscription.terminate(request.transform(unused ->
                    ImmutableNodes.newContainerBuilder()
                        .withNodeIdentifier(NodeIdentifier.create(DeleteSubscriptionOutput.QNAME))
                        .build()
                ), NoSuchSubscription.QNAME), MoreExecutors.directExecutor());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not send subscription delete notification", e);
        }
    }
}
