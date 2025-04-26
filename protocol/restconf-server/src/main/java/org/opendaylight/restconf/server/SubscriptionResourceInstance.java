/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.EventStreamResponse;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.LinkRelation;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceInstance;
import org.opendaylight.netconf.transport.http.rfc6415.XRD;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.ReceiverHolder;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTCONF subscription resource. Deals with dispatching HTTP requests to individual sub-resources as needed.
 */
@NonNullByDefault
final class SubscriptionResourceInstance extends WebHostResourceInstance {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionResourceInstance.class);
    private static final HeadersResponse OPTIONS_ONLY_METHOD_NOT_ALLOWED;
    private static final HeadersResponse OPTIONS_ONLY_OK;
    private static final HeadersResponse OPTIONS_RESPONSE;

    private final RestconfStream.Registry streamRegistry;
    private final int sseMaximumFragmentLength;
    private final int sseHeartbeatIntervalMillis;

    static {
        final var headers = new ReadOnlyHttpHeaders(true, HttpHeaderNames.ALLOW, "OPTIONS");
        OPTIONS_ONLY_METHOD_NOT_ALLOWED = new HeadersResponse(HttpResponseStatus.METHOD_NOT_ALLOWED, headers);
        OPTIONS_ONLY_OK = new HeadersResponse(HttpResponseStatus.OK, headers);
        OPTIONS_RESPONSE = new HeadersResponse(HttpResponseStatus.OK, new ReadOnlyHttpHeaders(true,
            HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS"));
    }

    SubscriptionResourceInstance(final String path, final RestconfStream.Registry streamRegistry,
            final int sseMaximumFragmentLength, final int sseHeartbeatIntervalMillis) {
        super(path);
        this.streamRegistry = requireNonNull(streamRegistry);
        this.sseMaximumFragmentLength = sseMaximumFragmentLength;
        this.sseHeartbeatIntervalMillis = sseHeartbeatIntervalMillis;
    }

    @Override
    public Response prepare(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final SegmentPeeler peeler, final XRD xrd) {
        final var restconf = xrd.lookupLink(LinkRelation.RESTCONF);
        if (restconf == null) {
            return EmptyResponse.NOT_FOUND;
        }
        if (!peeler.hasNext()) {
            return optionsOnlyResponse(method);
        }

        final var subscriptionId = peeler.next();
        return switch (method) {
            case GET -> prepareEventStream(targetUri, headers, subscriptionId, true);
            case HEAD -> prepareEventStream(targetUri, headers, subscriptionId, false);
            case OPTIONS -> OPTIONS_RESPONSE;
            default -> EmptyResponse.NOT_FOUND;
        };
    }

    private Response prepareEventStream(final URI targetUri, final HttpHeaders headers, final String subscriptionId,
            final boolean startStream) {
        if (!startStream) {
            return HeadersResponse.of(HttpResponseStatus.OK, HttpHeaderNames.CONTENT_TYPE,
                HttpHeaderValues.TEXT_EVENT_STREAM);
        }
        if (!headers.contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, false)) {
            return new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
        }

        final Uint32 id;
        try {
            id = Uint32.valueOf(subscriptionId);
        } catch (IllegalArgumentException e) {
            LOG.debug("Invalid subscription id {}", subscriptionId, e);
            return new EmptyResponse(HttpResponseStatus.BAD_REQUEST);
        }
        final var subscription = streamRegistry.lookupSubscription(id);
        if (subscription == null) {
            LOG.debug("Subscription for id {} not found", subscriptionId);
            return EmptyResponse.NOT_FOUND;
        }
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
        final var streamParams = EventStreamGetParams.of(QueryParameters.of());

        final var receiverName = streamRegistry.lookupSubscription(Uint32.valueOf(subscriptionId)).receiverName();
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

    @Override
    protected void removeRegistration() {
        // no-op
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

    private static HeadersResponse optionsOnlyResponse(final ImplementedMethod method) {
        return switch (method) {
            case OPTIONS -> OPTIONS_ONLY_OK;
            default -> OPTIONS_ONLY_METHOD_NOT_ALLOWED;
        };
    }
}
