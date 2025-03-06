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
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodingUnsupported;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionOutput;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
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
@NonNullByDefault
public final class EstablishSubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier SUBSCRIPTION_STREAM =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream").intern());
    private static final NodeIdentifier SUBSCRIPTION_TARGET =
            NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "target").intern());
    private static final NodeIdentifier SUBSCRIPTION_STREAM_FILTER =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream-filter").intern());
    private static final NodeIdentifier SUBSCRIPTION_ENCODING =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "encoding").intern());
    private static final NodeIdentifier ESTABLISH_SUBSCRIPTION_OUTPUT =
        NodeIdentifier.create(EstablishSubscriptionOutput.QNAME);
    private static final NodeIdentifier OUTPUT_ID =
        NodeIdentifier.create(QName.create(EstablishSubscriptionOutput.QNAME, "id").intern());

    private static final Set<QName> SUPPORTED_ENCODINGS = Set.of(
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909
            .EncodeJson$I.QNAME,
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909
            .EncodeXml$I.QNAME);

    private final SubscriptionStateService subscriptionStateService;
    private final SubscriptionStateMachine stateMachine;
    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public EstablishSubscriptionRpc(@Reference final RestconfStream.Registry streamRegistry,
            @Reference final SubscriptionStateService subscriptionStateService,
            @Reference final SubscriptionStateMachine stateMachine) {
        super(EstablishSubscription.QNAME);
        this.subscriptionStateService = requireNonNull(subscriptionStateService);
        this.stateMachine = requireNonNull(stateMachine);
        this.streamRegistry = requireNonNull(streamRegistry);
    }

    @Override
    public void invoke(final ServerRequest<ContainerNode> request, final URI restconfURI, final OperationInput input) {
        final var session = request.session();
        if (session == null) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED,
                "This end point does not support dynamic subscriptions."));
            return;
        }

        final var body = input.input();
        var encoding = leaf(body, SUBSCRIPTION_ENCODING, QName.class);
        if (encoding == null) {
            // FIXME: derive from request
            encoding =  org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909
                .EncodeJson$I.QNAME;
        } else if (!SUPPORTED_ENCODINGS.contains(encoding)) {
            request.completeWith(new RequestException(EncodingUnsupported.VALUE.toString()));
            return;
        }

        final var target = (ChoiceNode) body.childByArg(SUBSCRIPTION_TARGET);
        if (target == null) {
            // means there is no stream information present
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No stream specified"));
            return;
        }

        // check stream name
        final var streamName = leaf(target, SUBSCRIPTION_STREAM, String.class);
        if (streamName == null) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No stream specified"));
            return;
        }

        // check stream filter
        final var streamFilter = (ChoiceNode) target.childByArg(SUBSCRIPTION_STREAM_FILTER);
        final var filter = streamFilter == null ? null : SubscriptionUtil.extractFilter(streamFilter);

        streamRegistry.establishSubscription(request.transform(subscription -> {
            final var id = subscription.id();
            final var holder = new SubscriptionHolder(id, subscriptionStateService, stateMachine, streamRegistry);
            session.registerResource(holder);
            stateMachine.registerSubscription(session, id);
            stateMachine.moveTo(id, SubscriptionState.ACTIVE);

            return ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(ESTABLISH_SUBSCRIPTION_OUTPUT)
                .withChild(ImmutableNodes.leafNode(OUTPUT_ID, id))
                .build();
        }), streamName, encoding, filter);
    }
}
