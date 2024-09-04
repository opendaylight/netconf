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
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(factory = NettyEndpoint.FACTORY_NAME, service = NettyEndpoint.class)
public final class NettyEndpoint {
    public static final String FACTORY_NAME = "org.opendaylight.restconf.server.NettyEndpoint";

    private static final String PROP_CONFIGURATION = ".configuration";

    private final HTTPServer httpServer;

    @Activate
    public NettyEndpoint(@Reference final RestconfServer server, @Reference final PrincipalService principalService,
            @Reference final RestconfStream.Registry streamRegistry, final Map<String, ?> props) {
        this(server, principalService, streamRegistry, (NettyEndpointConfiguration) props.get(PROP_CONFIGURATION));
    }

    public NettyEndpoint(final RestconfServer server, final PrincipalService principalService,
            final RestconfStream.Registry streamRegistry, final NettyEndpointConfiguration configuration) {
        final var restconf = new NettyRestconf(streamRegistry, configuration);
        final var dispatcher = new RestconfRequestDispatcher(server, principalService, configuration.baseUri(),
            configuration.errorTagMapping(), configuration.defaultAcceptType(), configuration.prettyPrint());

        final var bootstrapFactory = new BootstrapFactory(configuration.groupName(), configuration.groupThreads());
        try {
            httpServer = HTTPServer.listen(restconf, bootstrapFactory.newServerBootstrap(),
                configuration.transportConfiguration(), dispatcher, principalService).get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Could not start RESTCONF server", e);
        }
    }

    @Deactivate
    public void deactivate() {
        try {
            httpServer.shutdown().get(1, TimeUnit.MINUTES);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new IllegalStateException("RESTCONF server shutdown failed", e);
        }
    }

    public static Map<String, ?> props(final NettyEndpointConfiguration configuration) {
        return Map.of(PROP_CONFIGURATION, configuration);
    }
}
