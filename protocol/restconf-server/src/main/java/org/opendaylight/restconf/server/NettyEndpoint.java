/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import javax.net.ssl.SSLException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceProvider;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
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
    private final @Nullable Http3ServerBootstrap http3ServerBootstrap;

    protected NettyEndpoint(final RestconfServer server, final PrincipalService principalService,
            final RestconfStream.Registry streamRegistry, final BootstrapFactory bootstrapFactory,
            final NettyEndpointConfiguration configuration) {
        final var listener = new RestconfTransportChannelListener(server, streamRegistry, principalService,
            configuration);
        try {
            httpServer = HTTPServer.listen(listener, bootstrapFactory.newServerBootstrap(),
                configuration.transportConfiguration()).get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Could not start RESTCONF server", e);
        }

        root = listener.root();
        http3ServerBootstrap = startHttp3(configuration, root);
    }

    @NonNullByDefault
    public final Registration registerWebResource(final WebHostResourceProvider provider) {
        return root.registerProvider(provider);
    }

    protected final ListenableFuture<Empty> shutdown() {
        if (http3ServerBootstrap != null) {
            http3ServerBootstrap.close();
        }
        return httpServer.shutdown();
    }

    private static @Nullable Http3ServerBootstrap startHttp3(final NettyEndpointConfiguration configuration,
            final EndpointRoot root) {
        final var certificate = configuration.tlsCertificate();
        final var privateKey = configuration.tlsPrivateKey();
        if (certificate == null || privateKey == null) {
            LOG.info("HTTP/3 is disabled because TLS is not configured");
            return null;
        }

        final var bindAddress = configuration.bindAddress();
        if (bindAddress == null || bindAddress.isBlank()) {
            LOG.warn("HTTP/3 is enabled but bind address is not configured");
            return null;
        }

        final var udpPort = configuration.bindPort();
        if (udpPort <= 0 || udpPort > 65535) {
            LOG.warn("HTTP/3 is enabled but UDP port is invalid: {}", udpPort);
            return null;
        }

        try {
            return Http3ServerBootstrap.start(bindAddress, udpPort, certificate, privateKey, root,
                configuration.chunkSize(), RestconfSessionBootstrap.MAX_HTTP2_CONTENT_LENGTH);
        } catch (SSLException e) {
            LOG.warn("Failed to initialize HTTP/3 TLS context", e);
            return null;
        }
    }
}
