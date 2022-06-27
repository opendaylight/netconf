/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.tcp;

import io.netty.channel.ChannelFuture;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.opendaylight.netconf.api.NetconfServerDispatcher;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create an MD-SAL NETCONF server using TCP.
 */
public class NetconfNorthboundTcpServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfNorthboundTcpServer.class);

    private final ChannelFuture tcpServer;

    public NetconfNorthboundTcpServer(final NetconfServerDispatcher netconfServerDispatcher, final String address,
            final String port) {
        final InetSocketAddress inetAddress = getInetAddress(address, port);
        tcpServer = netconfServerDispatcher.createServer(inetAddress);
        tcpServer.addListener(future -> {
            if (future.isDone() && future.isSuccess()) {
                LOG.info("Netconf TCP endpoint started successfully at {}", inetAddress);
            } else {
                LOG.warn("Unable to start TCP netconf server at {}", inetAddress, future.cause());
                throw new IllegalStateException("Unable to start TCP netconf server", future.cause());
            }
        });
    }

    private static InetSocketAddress getInetAddress(final String bindingAddress, final String portNumber) {
        final IpAddress ipAddress = IetfInetUtil.ipAddressFor(bindingAddress);
        final InetAddress inetAd = IetfInetUtil.INSTANCE.inetAddressFor(ipAddress);
        return new InetSocketAddress(inetAd, Integer.parseInt(portNumber));
    }

    @Override
    public void close() {
        if (tcpServer.isDone()) {
            tcpServer.channel().close();
        } else {
            tcpServer.cancel(true);
        }
    }
}
