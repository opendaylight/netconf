/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.util.concurrent.EventExecutor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.northbound.ssh.SshProxyServer;
import org.opendaylight.netconf.northbound.ssh.SshProxyServerConfigurationBuilder;
import org.opendaylight.netconf.server.api.NetconfServerDispatcher;
import org.opendaylight.netconf.shaded.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
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
 * NETCONF server for MD-SAL (listening by default on port 2830).
 */
@Component(service = { }, configurationPid = "org.opendaylight.netconf.ssh")
@Designate(ocd = NetconfNorthboundSshServer.Configuration.class)
public final class NetconfNorthboundSshServer implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String bindingAddress() default "0.0.0.0";
        @AttributeDefinition(min = "1", max = "65535")
        int portNumber() default 2830;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNorthboundSshServer.class);

    private final ChannelFuture localServer;
    private final SshProxyServer sshProxyServer;

    @Activate
    public NetconfNorthboundSshServer(
            @Reference final NetconfServerDispatcher netconfServerDispatcher,
            @Reference(target = "(type=global-worker-group)") final EventLoopGroup workerGroup,
            @Reference(target = "(type=global-event-executor)") final EventExecutor eventExecutor,
            @Reference(target = "(type=netconf-auth-provider)") final AuthProvider authProvider,
            final Configuration configuration) {
        this(netconfServerDispatcher, workerGroup, eventExecutor, authProvider, configuration.bindingAddress(),
            configuration.portNumber());
    }

    public NetconfNorthboundSshServer(final NetconfServerDispatcher netconfServerDispatcher,
            final EventLoopGroup workerGroup, final EventExecutor eventExecutor, final AuthProvider authProvider,
            final String bindingAddress, final int portNumber) {
        final LocalAddress localAddress = new LocalAddress(String.valueOf(portNumber));
        final var sshProxyServerConfiguration = new SshProxyServerConfigurationBuilder()
            .setBindingAddress(getInetAddress(bindingAddress, portNumber))
            .setLocalAddress(localAddress)
            .setAuthenticator(authProvider)
            .setIdleTimeout(Integer.MAX_VALUE)
            .setKeyPairProvider(new SimpleGeneratorHostKeyProvider())
            .createSshProxyServerConfiguration();

        localServer = netconfServerDispatcher.createLocalServer(localAddress);
        sshProxyServer = new SshProxyServer(Executors.newScheduledThreadPool(1), workerGroup, eventExecutor);

        localServer.addListener(future -> {
            if (future.isDone() && !future.isCancelled()) {
                try {
                    sshProxyServer.bind(sshProxyServerConfiguration);
                } catch (final IOException e) {
                    throw new IllegalStateException("Unable to start SSH netconf server", e);
                }
                LOG.info("Netconf SSH endpoint started successfully at {}", bindingAddress);
            } else {
                LOG.warn("Unable to start SSH netconf server at {}", bindingAddress, future.cause());
                throw new IllegalStateException("Unable to start SSH netconf server", future.cause());
            }
        });
    }

    @Deactivate
    @Override
    public void close() throws IOException {
        sshProxyServer.close();

        if (localServer.isDone()) {
            localServer.channel().close();
        } else {
            localServer.cancel(true);
        }
    }

    private static InetSocketAddress getInetAddress(final String bindingAddress, final int portNumber) {
        final var ipAddress = IetfInetUtil.ipAddressFor(bindingAddress);
        return new InetSocketAddress(IetfInetUtil.inetAddressFor(ipAddress), portNumber);
    }
}
