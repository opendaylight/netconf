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
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.List;
import org.opendaylight.netconf.server.api.NetconfServerFactory;
import org.opendaylight.netconf.shaded.sshd.server.SshServer;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.netconf.transport.ssh.ServerFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;

public final class NetconfServerFactoryImpl implements NetconfServerFactory {
    private static final TransportChannelListener EMPTY_LISTENER = new BaseTransportChannelListener();

    private final EventLoopGroup parentGroup;
    private final EventLoopGroup workerGroup;
    private final EventExecutor executor;
    private final ServerChannelInitializer channelInitializer;

    public NetconfServerFactoryImpl(final ServerChannelInitializer channelInitializer, final EventLoopGroup bossGroup,
            final EventLoopGroup workerGroup) {
        this(channelInitializer, bossGroup, workerGroup, GlobalEventExecutor.INSTANCE);
    }

    public NetconfServerFactoryImpl(final ServerChannelInitializer channelInitializer,
            final EventLoopGroup parentGroup, final EventLoopGroup workerGroup, final EventExecutor executor) {
        this.parentGroup = requireNonNull(parentGroup);
        this.workerGroup = requireNonNull(workerGroup);
        this.executor = requireNonNull(executor);
        this.channelInitializer = requireNonNull(channelInitializer);
    }

    @Override
    public ListenableFuture<TCPServer> createTcpServer(final TcpServerGrouping params)
            throws UnsupportedConfigurationException {
        return TCPServer.listen(new BaseServerTransport(executor, channelInitializer), createBootstrap(), params);
    }

    @Override
    public ListenableFuture<SSHServer> createSshServer(final TcpServerGrouping tcpParams,
            final SshServerGrouping sshParams, final ServerFactoryManagerConfigurator configurator)
                throws UnsupportedConfigurationException {
        return SSHServer.listen(EMPTY_LISTENER, createBootstrap(), tcpParams, sshParams, factoryManager -> {
            if (configurator != null) {
                configurator.configureServerFactoryManager(factoryManager);
            }
            if (factoryManager instanceof SshServer server) {
                server.setSubsystemFactories(List.of(new NetconfSubsystemFactory(channelInitializer)));
            }
        });
    }

    private ServerBootstrap createBootstrap() {
        return NettyTransportSupport.newServerBootstrap().group(parentGroup, workerGroup);
    }
}
