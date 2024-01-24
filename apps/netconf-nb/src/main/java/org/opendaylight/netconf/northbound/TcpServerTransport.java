/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import java.util.concurrent.ExecutionException;
import org.opendaylight.netconf.server.ServerTransportInitializer;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev231228.netconf.server.listen.stack.grouping.transport.tls.tls.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev231228.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create an MD-SAL NETCONF server using TCP.
 */
@Component(service = {}, configurationPid = "org.opendaylight.netconf.tcp", enabled = false)
@Designate(ocd = TcpServerTransport.Configuration.class)
public final class TcpServerTransport implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String bindingAddress() default "0.0.0.0";

        @AttributeDefinition(min = "1", max = "65535")
        int portNumber() default 2831;
    }

    private static final Logger LOG = LoggerFactory.getLogger(TcpServerTransport.class);

    private final TCPServer tcpServer;

    @Activate
    public TcpServerTransport(@Reference final TransportFactoryHolder factoryHolder,
            @Reference final OSGiNetconfServer backend, final Configuration configuration) {
        // FIXME: create an instantiation and do not use TLS
        this(factoryHolder, backend.serverTransportInitializer(), new TcpServerParametersBuilder()
            .setLocalAddress(IetfInetUtil.ipAddressFor(configuration.bindingAddress()))
            .setLocalPort(new PortNumber(Uint16.valueOf(configuration.portNumber())))
            .build());
    }

    public TcpServerTransport(final TransportFactoryHolder factoryHolder, final ServerTransportInitializer initializer,
            final TcpServerGrouping listenParams) {
        final var localAddr = listenParams.requireLocalAddress().stringValue();
        final var localPort = listenParams.requireLocalPort().getValue();

        try {
            tcpServer = TCPServer.listen(initializer, factoryHolder.factory().newServerBootstrap(), listenParams).get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            LOG.warn("Could not start TCP NETCONF server at {}:{}", localAddr, localPort, e);
            throw new IllegalStateException("Could not start TCP NETCONF server", e);
        }

        LOG.info("TCP NETCONF server at {}:{} started", localAddr, localPort);
    }

    @Deactivate
    @Override
    public void close() {
        try {
            tcpServer.shutdown().get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.warn("Could not stop TCP NETCONF server {}", tcpServer, e);
        }
    }
}
