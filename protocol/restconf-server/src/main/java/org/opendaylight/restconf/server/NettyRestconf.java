/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.http.SseUtils;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Glue between a {@link RestconfServer} and a transport stack instance.
 */
final class NettyRestconf implements TransportChannelListener {
    private static final Logger LOG = LoggerFactory.getLogger(NettyRestconf.class);

    private final RestconfStream.Registry streamRegistry;
    private final NettyEndpointConfiguration configuration;
    private final RestconfRequestDispatcher dispatcher;

    NettyRestconf(final RestconfServer server, final RestconfStream.Registry streamRegistry,
            final PrincipalService principalService, final NettyEndpointConfiguration configuration) {
        this.streamRegistry = requireNonNull(streamRegistry);
        this.configuration = requireNonNull(configuration);
        dispatcher = new RestconfRequestDispatcher(server, principalService, configuration.baseUri(),
            configuration.errorTagMapping(), configuration.defaultAcceptType(), configuration.prettyPrint());
    }

    @Override
    public void onTransportChannelEstablished(final TransportChannel channel) {
        final var nettyChannel = channel.channel();

        nettyChannel.pipeline().addLast(HTTPServer.REQUEST_DISPATCHER_HANDLER_NAME, new RestconfSession(dispatcher));

        SseUtils.enableServerSse(nettyChannel,
            new RestconfStreamService(streamRegistry, configuration.baseUri(), configuration.errorTagMapping(),
                configuration.defaultAcceptType(), configuration.prettyPrint()),
            configuration.sseMaximumFragmentLength().toJava(), configuration.sseHeartbeatIntervalMillis().toJava());
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        LOG.warn("Connection failed", cause);
    }
}
