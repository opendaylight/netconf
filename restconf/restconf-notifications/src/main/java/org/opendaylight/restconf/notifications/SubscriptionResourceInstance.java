/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications;

import static org.opendaylight.restconf.server.spi.RpcImplementation.leaf;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
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
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.server.ChannelSender;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.subscription.SubscriptionState;
import org.opendaylight.restconf.subscription.SubscriptionStateMachine;
import org.opendaylight.restconf.subscription.SubscriptionUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
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

    private final SubscriptionStateMachine machine;
    private final MdsalNotificationService mdsalService;
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

    SubscriptionResourceInstance(final String path, final SubscriptionStateMachine machine,
            final RestconfStream.Registry streamRegistry, final MdsalNotificationService mdsalService,
            final int sseMaximumFragmentLength, final int sseHeartbeatIntervalMillis) {
        super(path);
        this.machine = machine;
        this.mdsalService = mdsalService;
        this.streamRegistry = streamRegistry;
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
        // FIXME dealing with exceptions here is suboptimal - refactor with state machine
        try {
            final var subscriptionState = machine.getSubscriptionState(Uint32.valueOf(subscriptionId));
            if (subscriptionState != SubscriptionState.ACTIVE) {
                LOG.debug("Subscription for id {} is not active", subscriptionId);
                return new EmptyResponse(HttpResponseStatus.CONFLICT);
            }
        } catch (NoSuchElementException nse) {
            LOG.debug("Subscription for id {} not found", subscriptionId);
            return EmptyResponse.NOT_FOUND;
        }

        // Reading subscription from datastore to get stream name and encoding
        final String streamName;
        final String encodingName;
        try {
            final var subscription = mdsalService.read(SubscriptionUtil.SUBSCRIPTIONS.node(
                YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID,
                Uint32.valueOf(subscriptionId)))).get();
            if (subscription.isEmpty()) {
                LOG.warn("Could not send event stream response: could not read subscription");
                return EmptyResponse.NOT_FOUND;
            }
            // FIXME replace read with usage of plugin
            final var target = (DataContainerNode) ((DataContainerNode) subscription.orElseThrow())
                .childByArg(YangInstanceIdentifier.NodeIdentifier.create(SubscriptionUtil.QNAME_TARGET));
            streamName = leaf(target, YangInstanceIdentifier.NodeIdentifier.create(SubscriptionUtil.QNAME_STREAM),
                String.class);
            encodingName = leaf(target, YangInstanceIdentifier.NodeIdentifier.create(SubscriptionUtil.QNAME_ENCODING),
                String.class);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        final var stream = streamRegistry.lookupStream(streamName);
        if (stream == null) {
            LOG.debug("Stream '{}' not found", streamName);
            return EmptyResponse.NOT_FOUND;
        }

        final var decoder = new QueryStringDecoder(targetUri);
        final EventStreamGetParams streamParams;
        try {
            streamParams = EventStreamGetParams.of(QueryParameters.ofMultiValue(decoder.parameters()));
        } catch (IllegalArgumentException e) {
            return new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
        }

        final var sender = new ChannelSender(sseMaximumFragmentLength);

        // Encoding is optional field and in case it is absent json encoding will be used by default
        final var encoding = encodingName == null ? RestconfStream.EncodingName.RFC8040_JSON :
            new RestconfStream.EncodingName(encodingName);
        final var registration = registerSender(stream, encoding, streamParams,
            sender);

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

    private static HeadersResponse optionsOnlyResponse(final ImplementedMethod method) {
        return switch (method) {
            case OPTIONS -> OPTIONS_ONLY_OK;
            default -> OPTIONS_ONLY_METHOD_NOT_ALLOWED;
        };
    }
}
