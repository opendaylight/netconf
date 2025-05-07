/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.HTTPTransportChannel;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Glue between a {@link RestconfServer} and a transport stack instance.
 */
@NonNullByDefault
final class RestconfTransportChannelListener implements TransportChannelListener<HTTPTransportChannel> {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfTransportChannelListener.class);

    private final EndpointRoot root;

    RestconfTransportChannelListener(final RestconfServer server, final RestconfStream.Registry streamRegistry,
            final PrincipalService principalService, final NettyEndpointConfiguration configuration) {
        // Reconstruct root API path in encoded form
        final var apiRootPath = configuration.apiRootPath();
        final var sb = new StringBuilder();
        for (var segment : apiRootPath) {
            sb.append('/').append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
        }
        final var restconf = sb.toString();

        // Split apiRootPath into first segment and the rest
        final var firstSegment = apiRootPath.getFirst();
        final var otherSegments = apiRootPath.stream().skip(1).collect(Collectors.toUnmodifiableList());

        root = new EndpointRoot(principalService, new WellKnownResources(restconf), firstSegment,
            new APIResource(server, otherSegments, sb.append('/').toString(), configuration.errorTagMapping(),
                configuration.defaultEncoding(), configuration.prettyPrint(),
                configuration.sseHeartbeatIntervalMillis().intValue(),
                configuration.sseMaximumFragmentLength().intValue(), streamRegistry));

        LOG.info("Initialized with service {}", server.getClass());
        LOG.info("Initialized with base path: {}, default encoding: {}, default pretty print: {}", restconf,
            configuration.defaultEncoding(), configuration.prettyPrint().value());
    }

    EndpointRoot root() {
        return root;
    }

    @Override
    public void onTransportChannelEstablished(final HTTPTransportChannel channel) {
        final var session = new RestconfSession(channel.scheme(), channel.channel().remoteAddress(), root);
        final var nettyChannel = channel.channel();
        nettyChannel.pipeline().addLast(session);
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        LOG.warn("Connection failed", cause);
    }
}
