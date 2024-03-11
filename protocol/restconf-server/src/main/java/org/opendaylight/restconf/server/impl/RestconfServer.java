/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.AuthHandlerFactory;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
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

@Singleton
@Component(immediate = true,  configurationPid = "org.opendaylight.restconf.server")
@Designate(ocd = RestconfServer.Configuration.class)
public final class RestconfServer implements AutoCloseable {

    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String bindingAddress() default "0.0.0.0";

        @AttributeDefinition(min = "1", max = "65535")
        int portNumber() default 8182;
    }

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

    @Activate
    @Inject
    public RestconfServer(
            @Reference(target = "(type=restconf-server)") final AuthHandlerFactory authHandlerFactory,
            @Reference(target = "(type=restconf-server)") final RequestDispatcher requestDispatcher,
            final Configuration configuration) {

        final var localAddr = configuration.bindingAddress();
        final var localPort = configuration.portNumber();
        final var factory = new BootstrapFactory("restconf-server", 0);

        final var serverParameters = new HttpServerStackGrouping() {
            @Override
            public Class<? extends HttpServerStackGrouping> implementedInterface() {
                return HttpServerStackGrouping.class;
            }

            @Override
            public Transport getTransport() {
                return ConfigUtils.serverTransportTcp(configuration.bindingAddress(), configuration.portNumber());
            }
        };

        try {
            httpServer = HTTPServer.listen(LISTENER, factory.newServerBootstrap(), serverParameters,
                requestDispatcher, authHandlerFactory).get();
            LOG.info("RESTCONF server started at {}:{}", localAddr, localPort);

        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            LOG.warn("Could not start RESTCONF server at {}:{}", localAddr, localPort, e);
            throw new IllegalStateException(e);
        }
    }

    @Deactivate
    @Override
    public void close() throws Exception {
        httpServer.shutdown();
    }
}
