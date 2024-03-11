/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import java.util.concurrent.ExecutionException;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.AuthHandlerFactory;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RestconfServer {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfServer.class);

    private static final TransportChannelListener LISTENER = new TransportChannelListener() {
        @Override
        public void onTransportChannelEstablished(final TransportChannel channel) {
            LOG.debug("Connection established with {}", channel.channel().remoteAddress());
        }

        @Override
        public void onTransportChannelFailed(final Throwable cause) {
            LOG.warn("Connection failed", cause);
        }
    };

    private final HTTPServer httpServer;

    public RestconfServer(
            final AuthHandlerFactory authHandlerFactory,
            final RestconfRequestDispatcher requestDispatcher,
            final HttpServerStackGrouping listenConfig) {

        final var factory = new BootstrapFactory("restconf-server", 0);

        try {
            httpServer = HTTPServer.listen(LISTENER, factory.newServerBootstrap(), listenConfig,
                requestDispatcher, authHandlerFactory).get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Could not start RESTCONF server", e);
        }
    }

    public void shutdown() {
        httpServer.shutdown();
    }
}
