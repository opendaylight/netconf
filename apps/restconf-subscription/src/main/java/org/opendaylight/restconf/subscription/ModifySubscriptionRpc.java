/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.spi.RestconfStream.Registry;
import static org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;

import java.net.URI;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.stream.subtree.filter.StreamSubtreeFilter;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
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
@NonNullByDefault
public final class ModifySubscriptionRpc extends RpcImplementation {
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

    private final SubscriptionStateService subscriptionStateService;
    private final SubscriptionStateMachine stateMachine;
    private final Registry streamRegistry;

    @Inject
    @Activate
    public ModifySubscriptionRpc(@Reference final Registry streamRegistry,
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

        final var target = (ChoiceNode) body.childByArg(SUBSCRIPTION_TARGET);

        if (target == null) {
            // FIXME: is this correct??
            request.completeWith(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(NodeIdentifier.create(ModifySubscriptionOutput.QNAME))
                .build());
            LOG.debug("Subscription modified but nothing to change");
        }
        final var streamFilter = (ChoiceNode) target.childByArg(SUBSCRIPTION_STREAM_FILTER);
        final var filter = streamFilter == null ? null : extractFilter(streamFilter);
        if (filter == null) {
            // FIXME: is this correct??
            request.completeWith(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(NodeIdentifier.create(ModifySubscriptionOutput.QNAME))
                .build());
            LOG.debug("Subscription modified but nothing to change");
            return;
        }

        request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED,
            "Not implemented yet"));

        final var subscription = streamRegistry.lookupSubscription(id);
        if (subscription == null) {
            request.completeWith(new RequestException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "There is no active or suspended subscription with given ID."));
            return;
        }

        streamRegistry.modifySubscription(request.transform(unused -> {
            // is change state needed?
            stateMachine.moveTo(id, SubscriptionState.ACTIVE);

            try {
                // FIXME: pass correct filter once we extract if from input
                subscriptionStateService.subscriptionModified(Instant.now(), id, subscription.streamName(),
                    subscription.encoding(), null, null, null);
            } catch (InterruptedException e) {
                LOG.warn("Could not send subscription modify notification", e);
            }

            return ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(NodeIdentifier.create(ModifySubscriptionOutput.QNAME))
                .build();
        }), subscription, filter);
    }

    // FIXME: code duplicity
    private static @Nullable SubscriptionFilter extractFilter(final ChoiceNode streamFilter) {
        final var filterName = leaf(streamFilter, SUBSCRIPTION_STREAM_FILTER_NAME, String.class);
        if (filterName != null) {
            return new SubscriptionFilter.Reference(filterName);
        }
        final var filterSpec = (ChoiceNode) streamFilter.childByArg(new NodeIdentifier(FilterSpec.QNAME));
        if (filterSpec == null) {
            return null;
        }
        final var subtree = (AnydataNode<?>) filterSpec.childByArg(new NodeIdentifier(StreamSubtreeFilter.QNAME));
        if (subtree != null) {
            return new SubscriptionFilter.SubtreeDefinition(subtree);
        }
        final var xpath = leaf(filterSpec, new NodeIdentifier(QName.create(FilterSpec.QNAME, "stream-xpath-filter")),
            String.class);
        return xpath != null ? new SubscriptionFilter.XPathDefinition(xpath) : null;
    }
}
