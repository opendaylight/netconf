/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
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
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.server.api.NetconfServerFactory;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tls.TLSServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev221212.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev221212.TlsServerGrouping;

public class NetconfServerFactoryImpl implements NetconfServerFactory {

    private final EventLoopGroup parentGroup;
    private final EventLoopGroup workerGroup;
    private final TransportChannelListener transportChannelListener;

    public NetconfServerFactoryImpl(final ServerChannelInitializer channelInitializer, final EventLoopGroup bossGroup,
            final EventLoopGroup workerGroup) {
        this(channelInitializer, bossGroup, workerGroup, GlobalEventExecutor.INSTANCE);
    }

    public NetconfServerFactoryImpl(final ServerChannelInitializer channelInitializer,
            final EventLoopGroup parentGroup, final EventLoopGroup workerGroup, final EventExecutor executor) {
        this.parentGroup = requireNonNull(parentGroup);
        this.workerGroup = requireNonNull(workerGroup);
        this.transportChannelListener = new ChannelInitializerListener(channelInitializer, executor);
    }

    protected ServerBootstrap getBootstrap() {
        return NettyTransportSupport.newServerBootstrap().group(parentGroup, workerGroup);
    }

    @Override
    public ListenableFuture<TCPServer> createTcpServer(final TcpServerGrouping params)
            throws UnsupportedConfigurationException {
        return TCPServer.listen(transportChannelListener, getBootstrap(), params);
    }

    @Override
    public ListenableFuture<TLSServer> createTlsServer(final TcpServerGrouping tcpParams,
            final TlsServerGrouping tlsParams) throws UnsupportedConfigurationException {
        return TLSServer.listen(transportChannelListener, getBootstrap(), tcpParams, tlsParams);
    }

    @Override
    public ListenableFuture<SSHServer> createSshServer(final TcpServerGrouping tcpParams,
            final SshServerGrouping sshParams) throws UnsupportedConfigurationException {
        return SSHServer.listen(transportChannelListener, getBootstrap(), tcpParams, sshParams);
    }

    @Override
    public ListenableFuture<SSHServer> createSshServer(TcpServerGrouping tcpParams,
        Consumer<ServerFactoryManager> factoryInitializer) throws UnsupportedConfigurationException {
        return SSHServer.listen(transportChannelListener, getBootstrap(), tcpParams, factoryInitializer);
    }

    private record ChannelInitializerListener(
        ServerChannelInitializer channelInitializer, EventExecutor executor) implements TransportChannelListener {

        ChannelInitializerListener {
            requireNonNull(channelInitializer);
            requireNonNull(executor);
        }

        @Override
        public void onTransportChannelEstablished(@NonNull TransportChannel channel) {
            channelInitializer.initialize(channel.channel(), new DefaultPromise<>(executor));
        }

        @Override
        public void onTransportChannelFailed(@NonNull Throwable cause) {
            throw new IllegalStateException(cause);
        }
    }
}
