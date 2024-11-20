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
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EventStreamListener;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access to RFC8040 streams.
 *
 * <p>This class is created and initialized as part of Netty based RESTCONF northbound.
 */
@NonNullByDefault
final class StreamsResource extends AbstractLeafResource {
    private static final Logger LOG = LoggerFactory.getLogger(StreamsResource.class);

    private final EventStreamService service;

    StreamsResource(final EndpointInvariants invariants, final EventStreamService service) {
        super(invariants);
        this.service = requireNonNull(service);
    }

    @Override
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        return switch (method) {
            case GET -> prepareGet(session, targetUri, headers, principal, path, true);
            case HEAD -> prepareGet(session, targetUri, headers, principal, path, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private PreparedRequest prepareGet(final TransportSession session, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean startStream) {
        if (startStream) {
            final var peeler = new SegmentPeeler(targetUri);
            final var root = peeler.next() + "/" + peeler.next();
            final var listener = new StreamListener();
            service.startEventStream(root + path, listener,
                new StreamStartCallback(targetUri, listener));
        }
        return HeadersResponse.of(HttpResponseStatus.OK, HttpHeaderNames.CONTENT_TYPE,
            HttpHeaderValues.TEXT_EVENT_STREAM);
    }

    private record StreamStartCallback(URI targetUri, StreamListener listener)
            implements EventStreamService.StartCallback {
        private StreamStartCallback(final URI targetUri, final StreamListener listener) {
            this.targetUri = requireNonNull(targetUri);
            this.listener = requireNonNull(listener);
        }

        @Override
        public void onStreamStarted() {
            LOG.info("Stream for {} started.", targetUri);
        }

        @Override
        public void onStartFailure(final Exception exception) {
            LOG.info("Stream for {} failed.", targetUri);
            // FIXME create error response
        }
    }

    private static final class StreamListener implements EventStreamListener {
        private EventStreamService.StreamControl control;

        @Override
        public void onStreamStart(final EventStreamService.StreamControl control) {
            // this is called when we initially return 200 by prepareGet
            LOG.info("On stream start emitted.");
            this.control = control;
        }

        @Override
        public void onEventField(@NonNull final String fieldName, @NonNull final String fieldValue) {
            LOG.info("On event field emitted.");
            // FIXME add logic to emit HTTP chunk responses, see: ServerSseHandler for inspiration
        }

        @Override
        public void onStreamEnd() {
            // FIXME add logic to emit LAST HTTP response, see: ServerSseHandler for inspiration
            LOG.info("On stream end emitted.");
            if (control != null) {
                control.close();
            }
        }
    }
}
