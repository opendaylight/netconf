/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.AuthHandlerFactory;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(factory = NettyEndpoint.FACTORY_NAME, service = NettyEndpoint.class)
public final class NettyEndpoint implements AutoCloseable {
    public static final String FACTORY_NAME = "org.opendaylight.restconf.server.NettyEndpoint";

    private static final Logger LOG = LoggerFactory.getLogger(NettyEndpoint.class);
    private static final String PROP_CONFIGURATION = ".configuration";

    private final HTTPServer httpServer;

    @Activate
    public NettyEndpoint(@Reference final RestconfServer service,
            @Reference final AuthHandlerFactory authHandlerFactory,
            @Reference final RestconfStream.Registry streamRegistry, final Map<String, ?> props) {
        this(service, authHandlerFactory, streamRegistry, (NettyEndpointConfiguration) props.get(PROP_CONFIGURATION));
    }

    public NettyEndpoint(final RestconfServer service, final AuthHandlerFactory authHandlerFactory,
            final RestconfStream.Registry streamRegistry, final NettyEndpointConfiguration config) {

        final var bootstrapFactory = new BootstrapFactory(config.groupName(), config.groupThreads());
        final var dispatcher = new RestconfRequestDispatcher(service, config.restconf(),
            config.errorTagMapping(), config.defaultAcceptType(), config.prettyPrint());
        final var overlayListener = buildSseOverlayListener(streamRegistry, config);

        try {
            httpServer = HTTPServer.listen(overlayListener, bootstrapFactory.newServerBootstrap(),
                config.transportConfiguration(), dispatcher, authHandlerFactory).get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Could not start RESTCONF server", e);
        }
    }

    @Deactivate
    @Override
    public void close() {
        try {
            httpServer.shutdown().get(1, TimeUnit.MINUTES);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new IllegalStateException("RESTCONF server shutdown failed", e);
        }
    }

    public static Map<String, ?> props(final NettyEndpointConfiguration configuration) {
        return Map.of(PROP_CONFIGURATION, configuration);
    }

    private static TransportChannelListener buildSseOverlayListener(final RestconfStream.Registry streamRegistry,
            final NettyEndpointConfiguration config) {
        // TODO SSE Handler
        return new TransportChannelListener() {
            @Override
            public void onTransportChannelEstablished(final TransportChannel channel) {
                LOG.debug("Connection established with {}", channel.channel().remoteAddress());
            }

            @Override
            public void onTransportChannelFailed(final Throwable cause) {
                LOG.warn("Connection failed", cause);
            }
        };
    }
}
