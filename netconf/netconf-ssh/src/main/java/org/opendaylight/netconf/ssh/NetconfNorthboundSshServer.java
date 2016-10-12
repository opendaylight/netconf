/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.ssh;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.util.concurrent.EventExecutor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.opendaylight.netconf.api.NetconfServerDispatcher;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfNorthboundSshServer {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNorthboundSshServer.class);

    private final ChannelFuture localServer;
    private final SshProxyServer sshProxyServer;

    public NetconfNorthboundSshServer(final NetconfServerDispatcher netconfServerDispatcher,
                                      final EventLoopGroup workerGroup,
                                      final EventExecutor eventExecutor,
                                      final String bindingAddress,
                                      final String portNumber,
                                      final AuthProvider authProvider) {

        final LocalAddress localAddress = new LocalAddress(portNumber);

        localServer = netconfServerDispatcher.createLocalServer(localAddress);
        sshProxyServer = new SshProxyServer(Executors.newScheduledThreadPool(1), workerGroup, eventExecutor);

        final InetSocketAddress inetAddress = getInetAddress(bindingAddress, portNumber);
        final SshProxyServerConfigurationBuilder sshProxyServerConfigurationBuilder = new SshProxyServerConfigurationBuilder();
        sshProxyServerConfigurationBuilder.setBindingAddress(inetAddress);
        sshProxyServerConfigurationBuilder.setLocalAddress(localAddress);
        sshProxyServerConfigurationBuilder.setAuthenticator(authProvider);
        sshProxyServerConfigurationBuilder.setIdleTimeout(Integer.MAX_VALUE);
        sshProxyServerConfigurationBuilder.setKeyPairProvider(new PEMGeneratorHostKeyProvider());

        localServer.addListener(future -> {
            if (future.isDone() && !future.isCancelled()) {
                try {
                    sshProxyServer.bind(sshProxyServerConfigurationBuilder.createSshProxyServerConfiguration());
                    LOG.info("Netconf SSH endpoint started successfully at {}", bindingAddress);
                } catch (final IOException e) {
                    throw new RuntimeException("Unable to start SSH netconf server", e);
                }
            } else {
                LOG.warn("Unable to start SSH netconf server at {}", bindingAddress, future.cause());
                throw new RuntimeException("Unable to start SSH netconf server", future.cause());
            }
        });
    }

    private InetSocketAddress getInetAddress(final String bindingAddress, final String portNumber) {
        try {
            IpAddress ipAddress= IpAddressBuilder.getDefaultInstance(bindingAddress);
            final InetAddress inetAd = InetAddress.getByName(ipAddress.getIpv4Address() == null ? ipAddress.getIpv6Address().getValue() : ipAddress.getIpv4Address().getValue());
            return new InetSocketAddress(inetAd, Integer.valueOf(portNumber));
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Unable to bind netconf endpoint to address " + bindingAddress, e);
        }
    }

    public void close() {
        sshProxyServer.close();

        if (localServer.isDone()) {
            localServer.channel().close();
        } else {
            localServer.cancel(true);
        }
    }
}
