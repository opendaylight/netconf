/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceProvider;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverQuic;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class. Subclasess should interact with {@link #shutdown()}. See {@link OSGiNettyEndpoint} for a dynamic
 * example.
 */
public abstract class NettyEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(NettyEndpoint.class);

    private final HTTPServer httpServer;
    private final EndpointRoot root;
    private final @Nullable HTTPServer http3Server;

    protected NettyEndpoint(final RestconfServer server, final PrincipalService principalService,
            final RestconfStream.Registry streamRegistry, final BootstrapFactory bootstrapFactory,
            final NettyEndpointConfiguration configuration) {
        final var writeBufferWaterMark = new WriteBufferWaterMark(
            configuration.writeBufferLowWaterMark().intValue(),
            configuration.writeBufferHighWaterMark().intValue());
        final var listener = new RestconfTransportChannelListener(server, streamRegistry, principalService,
            configuration, writeBufferWaterMark);
        try {
            final var serverBootstrap = bootstrapFactory.newServerBootstrap()
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, writeBufferWaterMark);
            httpServer = HTTPServer.listen(listener, serverBootstrap,
                configuration.transportConfiguration()).get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Could not start RESTCONF server", e);
        }

        root = listener.root();
        http3Server = startHttp3(listener, configuration);
    }

    @NonNullByDefault
    public final Registration registerWebResource(final WebHostResourceProvider provider) {
        return root.registerProvider(provider);
    }

    protected final ListenableFuture<Empty> shutdown() {
        if (http3Server != null) {
            http3Server.shutdown();
        }
        return httpServer.shutdown();
    }

    private static @Nullable HTTPServer startHttp3(final RestconfTransportChannelListener listener,
            final NettyEndpointConfiguration configuration) {
        final var http3TransportConfiguration = configuration.http3TransportConfiguration();
        if (http3TransportConfiguration == null) {
            LOG.info("HTTP/3 is disabled because transport is not configured");
            return null;
        }

        final var http3AltSvcMaxAgeSeconds = configuration.http3AltSvcMaxAgeSeconds();
        if (http3AltSvcMaxAgeSeconds.longValue() == 0) {
            LOG.info("HTTP/3 is disabled because Alt-Svc max-age is {}", http3AltSvcMaxAgeSeconds);
            return null;
        }

        final var transport = http3TransportConfiguration.getTransport();
        if (!(transport instanceof HttpOverQuic httpOverQuicTransport)) {
            LOG.warn("HTTP/3 transport must be http-over-quic, got {}", transport);
            return null;
        }

        try {
            return HTTPServer.listen(listener, httpOverQuicTransport).get();
        } catch (UnsupportedConfigurationException | ExecutionException e) {
            LOG.warn("Failed to initialize HTTP/3 listener", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while initializing HTTP/3 listener", e);
            return null;
        }
    }
}
