/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;

/**
 * A factory capable of instantiating {@link SSHClient}s and {@link SSHServer}s.
 */
public final class SSHTransportStackFactory implements AutoCloseable {
    private final EventLoopGroup group;
    private final EventLoopGroup parentGroup;

    private SSHTransportStackFactory(final EventLoopGroup group, final EventLoopGroup parentGroup) {
        this.group = requireNonNull(group);
        this.parentGroup = parentGroup;
        // FIXME: factoryFactory = new NettyIoServiceFactoryFactory(group);
    }

    public SSHTransportStackFactory(final @NonNull String groupName, final int groupThreads) {
        this(null, NettyTransportSupport.newEventLoopGroup(groupName, groupThreads));
    }

    public SSHTransportStackFactory(final @NonNull String groupName, final int groupThreads,
            final @NonNull String parentGroupName, final int parentGroupThreads) {
        this(NettyTransportSupport.newEventLoopGroup(groupName, groupThreads),
            NettyTransportSupport.newEventLoopGroup(parentGroupName, parentGroupThreads));
    }

    public @NonNull ListenableFuture<SSHClient> connectClient(final TransportChannelListener listener,
            final TcpClientGrouping connectParams, final SshClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        return SSHClient.connect(listener, newBootstrap(), connectParams, clientParams);
    }

    public @NonNull ListenableFuture<SSHClient> listenClient(final TransportChannelListener listener,
            final TcpServerGrouping listenParams, final SshClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        return SSHClient.listen(listener, newServerBootstrap(), listenParams, clientParams);
    }

    public @NonNull ListenableFuture<SSHServer> connectServer(final TransportChannelListener listener,
            final TcpClientGrouping connectParams, final SshServerGrouping serverParams)
                throws UnsupportedConfigurationException {
        return SSHServer.connect(listener, newBootstrap(), connectParams, serverParams);
    }

    public @NonNull ListenableFuture<SSHServer> listenServer(final TransportChannelListener listener,
            final TcpServerGrouping connectParams, final SshServerGrouping serverParams)
                throws UnsupportedConfigurationException {
        return SSHServer.listen(listener, newServerBootstrap(), connectParams, serverParams);
    }

    public @NonNull ListenableFuture<SSHServer> listenServer(final TransportChannelListener listener,
            final TcpServerGrouping connectParams, final SshServerGrouping serverParams,
            final ServerFactoryManagerConfigurator configurator) throws UnsupportedConfigurationException {
        return SSHServer.listen(listener, newServerBootstrap(), connectParams, serverParams);
    }

    @Override
    public void close() {
        if (parentGroup != null) {
            parentGroup.shutdownGracefully();
        }
        group.shutdownGracefully();
    }

    private Bootstrap newBootstrap() {
        return NettyTransportSupport.newBootstrap().group(group);
    }

    private ServerBootstrap newServerBootstrap() {
        return NettyTransportSupport.newServerBootstrap().group(parentGroup != null ? parentGroup : group, group);
    }
}
