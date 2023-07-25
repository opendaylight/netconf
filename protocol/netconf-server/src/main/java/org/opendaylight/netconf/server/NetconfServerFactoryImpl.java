/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.server.api.NetconfServerFactory;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.shaded.sshd.server.SshServer;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.netconf.transport.ssh.ServerFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfServerFactoryImpl implements NetconfServerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfServerFactoryImpl.class);
    private static final TransportChannelListener EMPTY_LISTENER =  new ChannelInitializerListener(null, null);

    private final EventLoopGroup parentGroup;
    private final EventLoopGroup workerGroup;
    private final ServerChannelInitializer channelInitializer;
    private final TransportChannelListener transportChannelListener;

    public NetconfServerFactoryImpl(final ServerChannelInitializer channelInitializer, final EventLoopGroup bossGroup,
            final EventLoopGroup workerGroup) {
        this(channelInitializer, bossGroup, workerGroup, GlobalEventExecutor.INSTANCE);
    }

    public NetconfServerFactoryImpl(final ServerChannelInitializer channelInitializer,
            final EventLoopGroup parentGroup, final EventLoopGroup workerGroup, final EventExecutor executor) {
        this.parentGroup = requireNonNull(parentGroup);
        this.workerGroup = requireNonNull(workerGroup);
        this.channelInitializer = channelInitializer;
        transportChannelListener = new ChannelInitializerListener(channelInitializer, executor);
    }

    @NonNull protected ServerBootstrap createBootstrap() {
        return NettyTransportSupport.newServerBootstrap().group(parentGroup, workerGroup);
    }

    @Override
    public ListenableFuture<TCPServer> createTcpServer(final TcpServerGrouping params)
            throws UnsupportedConfigurationException {
        return TCPServer.listen(transportChannelListener, createBootstrap(), params);
    }

    @Override
    public ListenableFuture<SSHServer> createSshServer(final TcpServerGrouping tcpParams,
            final SshServerGrouping sshParams) throws UnsupportedConfigurationException {
        return SSHServer.listen(EMPTY_LISTENER, createBootstrap(), tcpParams, sshParams);
    }

    @Override
    public ListenableFuture<SSHServer> createSshServer(final TcpServerGrouping tcpParams,
        final SshServerGrouping sshParams, final ServerFactoryManagerConfigurator configurator)
            throws UnsupportedConfigurationException {
        return SSHServer.listen(EMPTY_LISTENER, createBootstrap(), tcpParams, sshParams,
            new FactoryManagerConfigurator(channelInitializer, configurator));
    }

    private record ChannelInitializerListener(
            @Nullable ServerChannelInitializer channelInitializer,
            @Nullable EventExecutor executor) implements TransportChannelListener {
        @Override
        public void onTransportChannelEstablished(final TransportChannel channel) {
            LOG.debug("Transport channel {} established", channel);
            if (channelInitializer != null && executor != null) {
                channelInitializer.initialize(channel.channel(), new DefaultPromise<>(executor));
            }
        }

        @Override
        public void onTransportChannelFailed(final Throwable cause) {
            LOG.error("Transport channel failed", cause);
        }
    }

    private record FactoryManagerConfigurator(
            @NonNull ServerChannelInitializer channelInitializer,
            @Nullable ServerFactoryManagerConfigurator configurator) implements ServerFactoryManagerConfigurator {
        FactoryManagerConfigurator {
            requireNonNull(channelInitializer);
        }

        @Override
        public void configureServerFactoryManager(final ServerFactoryManager factoryManager)
                throws UnsupportedConfigurationException {
            if (configurator != null) {
                configurator.configureServerFactoryManager(factoryManager);
            }
            if (factoryManager instanceof SshServer server) {
                server.setSubsystemFactories(List.of(new NetconfSubsystemFactory(channelInitializer)));
            }
        }
    }
}
