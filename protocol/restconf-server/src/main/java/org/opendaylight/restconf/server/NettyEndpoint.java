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
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceProvider;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * Abstract base class. Subclasess should interact with {@link #shutdown()}. See {@link OSGiNettyEndpoint} for a dynamic
 * example.
 */
public abstract class NettyEndpoint {
    private final HTTPServer httpServer;
    private final EndpointRoot root;

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
    }

    @NonNullByDefault
    public final Registration registerWebResource(final WebHostResourceProvider provider) {
        return root.registerProvider(provider);
    }

    protected final ListenableFuture<Empty> shutdown() {
        return httpServer.shutdown();
    }
}
