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
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodingUnsupported;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
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

    private final MdsalNotificationService mdsalService;
    private final SubscriptionStateService subscriptionStateService;
    private final SubscriptionStateMachine stateMachine;
    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public EstablishSubscriptionRpc(@Reference final MdsalNotificationService mdsalService,
            @Reference final SubscriptionStateService subscriptionStateService,
            @Reference final SubscriptionStateMachine stateMachine,
            @Reference final RestconfStream.Registry streamRegistry) {
        super(EstablishSubscription.QNAME);
        this.mdsalService = requireNonNull(mdsalService);
        this.subscriptionStateService = requireNonNull(subscriptionStateService);
        this.stateMachine = requireNonNull(stateMachine);
        this.streamRegistry = requireNonNull(streamRegistry);
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
        var encoding = leaf(body, SUBSCRIPTION_ENCODING, QName.class);
        if (encoding == null) {
            // FIXME: derive from request
        } else if (!SUPPORTED_ENCODINGS.contains(encoding)) {
            request.completeWith(new ServerException(EncodingUnsupported.VALUE.toString()));
            return;
        }

        final var target = (DataContainerNode) body.childByArg(SUBSCRIPTION_TARGET);
        if (target == null) {
            // means there is no stream information present
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No stream specified"));
            return;
        }

        // check stream name
        final var streamName = leaf(target, SUBSCRIPTION_STREAM, String.class);
        if (streamName == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No stream specified"));
            return;
        }

        // check stream filter
        final var streamFilter = (DataContainerNode) target.childByArg(SUBSCRIPTION_STREAM_FILTER);
        final String filterName;
        if (streamFilter != null) {
            filterName = leaf(streamFilter, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
            if (filterName != null) {
                try {
                    if (!mdsalService.exist(SubscriptionUtil.FILTERS.node(NodeIdentifierWithPredicates.of(
                            StreamFilter.QNAME, SubscriptionUtil.QNAME_STREAM_FILTER_NAME, filterName))).get()) {
                        request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                            "%s refers to an unknown stream filter", filterName));
                        return;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
                    return;
                }
            }
            //  TODO: parse anydata filter, rfc6241? https://www.rfc-editor.org/rfc/rfc8650#name-filter-example
            //    {@link StreamSubtreeFilter}.
        } else {
            filterName = null;
        }

        streamRegistry.establishSubscription(request.transform(subscription -> {
            final var id = subscription.id();
            final var holder = new SubscriptionHolder(subscription, subscriptionStateService, stateMachine);
            session.registerResource(holder);
            stateMachine.registerSubscription(session, id);
            stateMachine.moveTo(id, SubscriptionState.ACTIVE);

            return ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(ESTABLISH_SUBSCRIPTION_OUTPUT)
                .withChild(ImmutableNodes.leafNode(OUTPUT_ID, id))
                .build();
        }), streamName, encoding, filterName);
    }
}
