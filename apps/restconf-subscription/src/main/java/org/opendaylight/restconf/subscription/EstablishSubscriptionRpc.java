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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionFilter;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodingUnsupported;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.FilterSpec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamSubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.stream.filter.elements.filter.spec.StreamXpathFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.Target;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
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
    private static final NodeIdentifier ENCODING_NODEID =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "encoding").intern());
    private static final NodeIdentifier FILTER_SPEC_NODEID = NodeIdentifier.create(FilterSpec.QNAME);
    private static final NodeIdentifier ID_NODEID =
        NodeIdentifier.create(QName.create(EstablishSubscriptionOutput.QNAME, "id").intern());
    private static final NodeIdentifier OUTPUT_NODEID = NodeIdentifier.create(EstablishSubscriptionOutput.QNAME);
    private static final NodeIdentifier STOP_TIME_NODEID =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stop-time").intern());
    private static final NodeIdentifier STREAM_NODEID = NodeIdentifier.create(Stream.QNAME);
    private static final NodeIdentifier STREAM_FILTER_NODEID = NodeIdentifier.create(StreamFilter.QNAME);
    private static final NodeIdentifier STREAM_FILTER_NAME_NODEID =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "stream-filter-name").intern());
    private static final NodeIdentifier STREAM_SUBTREE_FILTER_NODEID = NodeIdentifier.create(StreamSubtreeFilter.QNAME);
    private static final NodeIdentifier STREAM_XPATH_FILTER_NODEID = NodeIdentifier.create(StreamXpathFilter.QNAME);
    private static final NodeIdentifier TARGET_NODEID = NodeIdentifier.create(Target.QNAME);

    private static final Set<QName> SUPPORTED_ENCODINGS = Set.of(
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909
            .EncodeJson$I.QNAME,
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909
            .EncodeXml$I.QNAME);

    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public EstablishSubscriptionRpc(@Reference final RestconfStream.Registry streamRegistry) {
        super(EstablishSubscription.QNAME);
        this.streamRegistry = requireNonNull(streamRegistry);
    }

    @Override
    public void invoke(final ServerRequest<ContainerNode> request, final URI restconfURI, final OperationInput input) {
        final var body = input.input();
        var encoding = leaf(body, ENCODING_NODEID, QName.class);
        if (encoding == null) {
            // FIXME: derive from request
            encoding = org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909
                .EncodeJson$I.QNAME;
        } else if (!SUPPORTED_ENCODINGS.contains(encoding)) {
            request.failWith(new RequestException(EncodingUnsupported.VALUE.toString()));
            return;
        }

        final var target = (ChoiceNode) body.childByArg(TARGET_NODEID);
        if (target == null) {
            // means there is no stream information present
            request.failWith(new RequestException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No stream specified"));
            return;
        }

        // check stream name
        final var streamName = leaf(target, STREAM_NODEID, String.class);
        if (streamName == null) {
            request.failWith(new RequestException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No stream specified"));
            return;
        }

        // check stream filter
        final var streamFilter = (ChoiceNode) target.childByArg(STREAM_FILTER_NODEID);
        final var filter = streamFilter == null ? null : extractFilter(streamFilter);

        // check stop-time
        final var stopTime = leaf(body, STOP_TIME_NODEID, String.class);
        final Instant stopInstant;
        if (stopTime != null) {
            try {
                stopInstant = Instant.parse(stopTime);
            } catch (DateTimeParseException e) {
                request.failWith(new RequestException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                    "Malformed stop-type", e));
                return;
            }
        } else {
            stopInstant = null;
        }

        streamRegistry.establishSubscription(request.transform(subscriptionId -> ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(OUTPUT_NODEID)
            .withChild(ImmutableNodes.leafNode(ID_NODEID, subscriptionId))
            .build()
        ), streamName, encoding, filter, stopInstant);
    }

    static @Nullable SubscriptionFilter extractFilter(final ChoiceNode streamFilter) {
        final var streamFilterName = (LeafNode<?>) streamFilter.childByArg(STREAM_FILTER_NAME_NODEID);
        if (streamFilterName != null) {
            return new SubscriptionFilter.Reference((String) streamFilterName.body());
        }
        final var filterSpec = (ChoiceNode) streamFilter.childByArg(FILTER_SPEC_NODEID);
        if (filterSpec == null) {
            return null;
        }
        final var subtree = (AnydataNode<?>) filterSpec.childByArg(STREAM_SUBTREE_FILTER_NODEID);
        if (subtree != null) {
            return new SubscriptionFilter.SubtreeDefinition(subtree);
        }
        final var xpath = (LeafNode<?>) filterSpec.childByArg(STREAM_XPATH_FILTER_NODEID);
        if (xpath != null) {
            return new SubscriptionFilter.XPathDefinition((String) xpath.body());
        }
        return null;
    }
}
