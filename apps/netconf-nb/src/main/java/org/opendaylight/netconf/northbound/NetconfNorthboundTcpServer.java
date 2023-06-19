/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import org.opendaylight.netconf.server.api.NetconfServerDispatcher;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create an MD-SAL NETCONF server using TCP.
 */
@Component(service = { }, configurationPid = "org.opendaylight.netconf.tcp", enabled = false)
@Designate(ocd = NetconfNorthboundTcpServer.Configuration.class)
public final class NetconfNorthboundTcpServer implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String bindingAddress() default "0.0.0.0";
        @AttributeDefinition(min = "1", max = "65535")
        int portNumber() default 2831;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNorthboundTcpServer.class);

    private final ChannelFuture tcpServer;

    @Activate
    public NetconfNorthboundTcpServer(
            @Reference(target = "(type=netconf-server-dispatcher)") final NetconfServerDispatcher serverDispatcher,
            final Configuration configuration) {
        this(serverDispatcher, configuration.bindingAddress(), configuration.portNumber());
    }

    public NetconfNorthboundTcpServer(final NetconfServerDispatcher serverDispatcher, final String address,
            final int port) {
        final InetSocketAddress inetAddress = getInetAddress(address, port);
        tcpServer = serverDispatcher.createServer(inetAddress);
        tcpServer.addListener(future -> {
            if (future.isDone() && future.isSuccess()) {
                LOG.info("Netconf TCP endpoint started successfully at {}", inetAddress);
            } else {
                LOG.warn("Unable to start TCP netconf server at {}", inetAddress, future.cause());
                throw new IllegalStateException("Unable to start TCP netconf server", future.cause());
            }
        });
    }

    @Override
    public void close() {
        if (tcpServer.isDone()) {
            tcpServer.channel().close();
        } else {
            tcpServer.cancel(true);
        }
    }

    private static InetSocketAddress getInetAddress(final String bindingAddress, final int portNumber) {
        final var inetAd = IetfInetUtil.inetAddressFor(IetfInetUtil.ipAddressFor(bindingAddress));
        return new InetSocketAddress(inetAd, portNumber);
    }
}
