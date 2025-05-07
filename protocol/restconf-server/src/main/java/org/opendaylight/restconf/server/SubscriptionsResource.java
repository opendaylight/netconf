/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.Principal;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.EventStreamResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.ReceiverHolder;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.Subscription;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RFC8650-compliant access to RFC8639 notification subscriptions.
 */
@NonNullByDefault
final class SubscriptionsResource extends AbstractEventStreamResource {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionsResource.class);

    SubscriptionsResource(final EndpointInvariants invariants, final RestconfStream.Registry streamRegistry,
            final int sseHeartbeatIntervalMillis, final int sseMaximumFragmentLength) {
        super(invariants, streamRegistry, sseHeartbeatIntervalMillis, sseMaximumFragmentLength);
    }

    @Override
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String firstSegment,
            final SegmentPeeler peeler) {
        // Subscriptions do not have nested resources
        if (peeler.hasNext()) {
            return new EmptyResponse(HttpResponseStatus.NOT_FOUND);
        }

        final Uint32 subscriptionId;
        try {
            subscriptionId = Uint32.valueOf(firstSegment);
        } catch (IllegalArgumentException e) {
            LOG.debug("Invalid subscription id {}", firstSegment, e);
            return new EmptyResponse(HttpResponseStatus.NOT_FOUND);
        }

        final var subscription = streamRegistry.lookupSubscription(subscriptionId);
        if (subscription == null) {
            LOG.debug("Subscription for id {} not found", subscriptionId);
            return EmptyResponse.NOT_FOUND;
        }

        return switch (method) {
            case GET -> prepareGet(headers, subscription);
            case HEAD -> EVENT_STREAM_HEAD;
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private PreparedRequest prepareGet(final HttpHeaders headers, final Subscription subscription) {
        if (!headers.contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, false)) {
            return new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
        }

        // FIXME: the rest of this processing should be encapsulated in a PreparedRequest, which talks to the
        //        Subscription only.

        final var subscriptionId = subscription.id();
        if (subscription.state() != SubscriptionState.ACTIVE) {
            LOG.debug("Subscription for id {} is not active", subscriptionId);
            return new EmptyResponse(HttpResponseStatus.CONFLICT);
        }

        final var streamName = subscription.streamName();
        final var stream = streamRegistry.lookupStream(streamName);
        if (stream == null) {
            LOG.debug("Stream '{}' not found", streamName);
            return EmptyResponse.NOT_FOUND;
        }

        // FIXME: this is bogus: all of this is already specified in subscription
        final var streamParams = EventStreamGetParams.of(QueryParameters.of());

        final var receiverName = subscription.receiverName();
        final var receiver = new ReceiverHolder(subscriptionId, receiverName, streamRegistry);
        final var sender = new ChannelSenderSubscription(sseMaximumFragmentLength, receiver);
        // Encoding is optional field and in case it is absent json encoding will be used by default
        final var encoding = encodingNameOf(subscription.encoding());
        final var registration = registerSender(stream, encoding, streamParams, sender);

        if (registration == null) {
            return EmptyResponse.NOT_FOUND;
        }

        sender.enable(registration);

        return new EventStreamResponse(HttpResponseStatus.OK, sender, sseHeartbeatIntervalMillis);
    }

    private static @Nullable Registration registerSender(final RestconfStream<?> stream,
            final RestconfStream.EncodingName encoding, final EventStreamGetParams params,
            final RestconfStream.Sender sender) {
        final Registration reg;
        try {
            reg = stream.addSubscriber(sender, encoding, params);
        } catch (UnsupportedEncodingException | XPathExpressionException e) {
            // FIXME: report an error
            return null;
        }
        return reg;
    }

    private static RestconfStream.EncodingName encodingNameOf(final QName identity) {
        if (identity.equals(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications
            .rev190909.EncodeJson$I.QNAME)) {
            return RestconfStream.EncodingName.RFC8040_JSON;
        }
        if (identity.equals(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications
            .rev190909.EncodeXml$I.QNAME)) {
            return RestconfStream.EncodingName.RFC8040_XML;
        }
        throw new IllegalArgumentException("Unsupported encoding " + identity);
    }
}
