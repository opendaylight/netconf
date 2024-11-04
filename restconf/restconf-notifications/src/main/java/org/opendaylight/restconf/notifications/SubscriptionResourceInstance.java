/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceInstance;
import org.opendaylight.netconf.transport.http.rfc6415.XRD;
import org.opendaylight.restconf.server.ChannelSender;
import org.opendaylight.restconf.subscription.SubscriptionStateMachine;
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

    private final SubscriptionStateMachine machine;

    static {
        final var headers = new ReadOnlyHttpHeaders(true, HttpHeaderNames.ALLOW, "OPTIONS");
        OPTIONS_ONLY_METHOD_NOT_ALLOWED = new HeadersResponse(HttpResponseStatus.METHOD_NOT_ALLOWED, headers);
        OPTIONS_ONLY_OK = new HeadersResponse(HttpResponseStatus.OK, headers);
    }

    SubscriptionResourceInstance(final String path, final SubscriptionStateMachine machine) {
        super(path);
        this.machine = machine;
    }

    @Override
    public Response prepare(final ImplementedMethod method, final URI targetUri, final HttpHeaders headers,
            final SegmentPeeler peeler, final XRD xrd) {
        if (!peeler.hasNext()) {
            return optionsOnlyResponse(method);
        }

        final var subscriptionId = peeler.next();
        return switch (method) {
            case GET -> prepareEventStream(method, targetUri, headers, subscriptionId, xrd);
            case HEAD, OPTIONS -> EmptyResponse.NOT_FOUND; // TODO
            default -> EmptyResponse.NOT_FOUND;
        };
    }

    private Response prepareEventStream(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final String subscriptionId, final XRD xrd) {
        if (!headers.contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, false)) {
            return new EmptyResponse(HttpResponseStatus.NOT_ACCEPTABLE);
        }

        final var sender = new ChannelSender(7); // FIXME do not hardcode it
//        final Registration registration = registerSender(stream, encoding, params, sender);

//        if (registration == null) {
//            return EmptyResponse.NOT_FOUND;
//        }
//
//        sender.enable(registration);

        return new HeadersResponse(HttpResponseStatus.OK, new ReadOnlyHttpHeaders(true, HttpHeaderNames.CONTENT_TYPE,
            HttpHeaderValues.TEXT_EVENT_STREAM));
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
