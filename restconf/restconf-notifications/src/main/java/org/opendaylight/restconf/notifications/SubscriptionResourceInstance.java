/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications;

import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.EmptyRequestResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.RequestResponse;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceInstance;
import org.opendaylight.netconf.transport.http.rfc6415.XRD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTCONF subscription resource. Deals with dispatching HTTP requests to individual sub-resources as needed.
 */
@NonNullByDefault
final class SubscriptionResourceInstance extends WebHostResourceInstance {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionResourceInstance.class);
    private static final EmptyRequestResponse OPTIONS_ONLY_METHOD_NOT_ALLOWED;
    private static final EmptyRequestResponse OPTIONS_ONLY_OK;

    private static final Map<Long, SubscriptionEventListener> LISTENERS = new HashMap<>();

    static {
        final var headers = DefaultHttpHeadersFactory.headersFactory().newHeaders()
            .set(HttpHeaderNames.ALLOW, "OPTIONS");
        OPTIONS_ONLY_METHOD_NOT_ALLOWED = new EmptyRequestResponse(HttpResponseStatus.METHOD_NOT_ALLOWED, headers);
        OPTIONS_ONLY_OK = new EmptyRequestResponse(HttpResponseStatus.OK, headers);
    }

    static {
        final var headers = DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
            .set(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS");
    }

    SubscriptionResourceInstance(final String path) {
        super(path);
    }

    @Override
    public RequestResponse prepare(final ImplementedMethod method, final URI targetUri, final HttpHeaders headers,
            final SegmentPeeler peeler, final XRD xrd) {
        if (!peeler.hasNext()) {
            return optionsOnlyResponse(method);
        }

        final var segment = peeler.next();
        if (segment.equals("subscription")) {
            return startEventStream(method, targetUri, headers, peeler, xrd);
        }
        return EmptyRequestResponse.NOT_FOUND;
    }

    private RequestResponse startEventStream(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final SegmentPeeler peeler, final XRD xrd) {
        // add new listener to map
        // SubscriptionEventListener#onStreamStart

        // return initial SSE response
        return null;
    }

    @Override
    protected void removeRegistration() {
        // stop all listeners, clear
        LISTENERS.clear();
    }

    private static EmptyRequestResponse optionsOnlyResponse(final ImplementedMethod method) {
        return switch (method) {
            case OPTIONS -> OPTIONS_ONLY_OK;
            default -> OPTIONS_ONLY_METHOD_NOT_ALLOWED;
        };
    }
}
