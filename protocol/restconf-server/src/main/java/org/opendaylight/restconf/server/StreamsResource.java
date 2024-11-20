/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import java.security.Principal;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EventStreamListener;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access to RFC8040 streams.
 */
@NonNullByDefault
final class StreamsResource extends AbstractLeafResource implements EventStreamListener {
    private static final Logger LOG = LoggerFactory.getLogger(StreamsResource.class);

    private final EventStreamService service;

    StreamsResource(final EndpointInvariants invariants, final EventStreamService service) {
        super(invariants);
        this.service = Objects.requireNonNull(service);
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
            final @Nullable Principal principal, final String path, final boolean withContent) {
        service.startEventStream(path, this, new EventStreamService.StartCallback() {
            @Override
            public void onStreamStarted(final EventStreamService.StreamControl streamControl) {
                LOG.info("Stream for {} started.", targetUri);
                // FIXME create sucess response
            }

            @Override
            public void onStartFailure(final Exception exception) {
                LOG.info("Stream for {} failed.", targetUri);
              // FIXME create error response
            }
        });

        return AbstractPendingOptions.READ_ONLY;
    }

    @Override
    public void onStreamStart() {
        LOG.info("On stream start emitted.");
    }

    @Override
    public void onEventField(@NonNull final String fieldName, @NonNull final String fieldValue) {
        LOG.info("On event field emitted.");
    }

    @Override
    public void onStreamEnd() {
        LOG.info("On stream end emitted.");
    }
}
