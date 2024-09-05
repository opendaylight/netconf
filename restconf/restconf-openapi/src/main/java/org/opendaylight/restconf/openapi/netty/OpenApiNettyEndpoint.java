/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.server.PrincipalService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, configurationPid = "org.opendaylight.restconf.openapi.netty")
@Designate(ocd = OpenApiNettyEndpoint.Configuration.class)
public final class OpenApiNettyEndpoint {
    @ObjectClassDefinition
    public @interface Configuration {

        @AttributeDefinition(description = "The hostname to be used for URLs constructed on server side")
        String host$_$name() default "localhost";

        @AttributeDefinition(description = "Restconf server address")
        String restconf$_$server$_$address() default "http://localhost:8182";

        @AttributeDefinition
        String bind$_$address() default "0.0.0.0";

        @AttributeDefinition(min = "1", max = "65535")
        int bind$_$port() default 8184;

        @AttributeDefinition(description = "Thread name prefix to be used by Netty's thread executor")
        String group$_$name() default "restconf-openapi-netty";

        @AttributeDefinition(min = "0", description = "Netty's thread limit. 0 means no limits.")
        int group$_$threads() default 0;
    }

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiNettyEndpoint.class);
    private static final String BASE_URI_FORMAT = "http://%s:%d/openapi";
    private final BootstrapFactory bootstrapFactory;
    private final HTTPServer httpServer;

    @Activate
    public OpenApiNettyEndpoint(@Reference final PrincipalService principalService, final Configuration config) {
        bootstrapFactory = new BootstrapFactory(config.group$_$name(), config.group$_$threads());
        final var baseUri = URI.create(BASE_URI_FORMAT.formatted(config.host$_$name(), config.bind$_$port()));
        final var restconfServerUri = URI.create(config.restconf$_$server$_$address());
        final var dispatcher = new OpenApiRequestDispatcher(principalService, baseUri, restconfServerUri);
        try {
            httpServer = HTTPServer.listen(transportChannelListener(dispatcher),
                bootstrapFactory.newServerBootstrap(),
                listenConfiguration(config), principalService).get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Could not start RESTCONF OpenAPI server", e);
        }
    }

    @Deactivate
    public void deactivate() {
        try {
            httpServer.shutdown().get(1, TimeUnit.MINUTES);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new IllegalStateException("RESTCONF OpenAPI server shutdown failed", e);
        } finally {
            bootstrapFactory.close();
        }
    }

    private static HttpServerStackGrouping listenConfiguration(final Configuration configuration) {
        final var transport =
            ConfigUtils.serverTransportTcp(configuration.bind$_$address(), configuration.bind$_$port());
        return new HttpServerStackGrouping() {
            @Override
            public Class<? extends HttpServerStackGrouping> implementedInterface() {
                return HttpServerStackGrouping.class;
            }

            @Override
            public Transport getTransport() {
                return transport;
            }
        };
    }

    private static TransportChannelListener transportChannelListener(final OpenApiRequestDispatcher dispatcher) {
        return new TransportChannelListener() {
            @Override
            public void onTransportChannelEstablished(@NonNull TransportChannel channel) {
                channel.channel().pipeline().addLast(new DispatcherHandler(dispatcher));
            }

            @Override
            public void onTransportChannelFailed(@NonNull Throwable cause) {
                LOG.warn("Connection failed", cause);
            }
        };
    }
}
