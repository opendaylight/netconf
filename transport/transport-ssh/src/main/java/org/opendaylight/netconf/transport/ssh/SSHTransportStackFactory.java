/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.shaded.sshd.server.subsystem.SubsystemFactory;
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
    private final NettyIoServiceFactoryFactory ioServiceFactory;

    private SSHTransportStackFactory(final EventLoopGroup group, final EventLoopGroup parentGroup) {
        this.group = requireNonNull(group);
        this.parentGroup = parentGroup;
        ioServiceFactory = new NettyIoServiceFactoryFactory(group);
    }

    public SSHTransportStackFactory(final @NonNull String groupName, final int groupThreads) {
        this(NettyTransportSupport.newEventLoopGroup(groupName, groupThreads), null);
    }

    public SSHTransportStackFactory(final @NonNull String groupName, final int groupThreads,
            final @NonNull String parentGroupName, final int parentGroupThreads) {
        this(NettyTransportSupport.newEventLoopGroup(groupName, groupThreads),
            NettyTransportSupport.newEventLoopGroup(parentGroupName, parentGroupThreads));
    }

    public @NonNull ListenableFuture<SSHClient> connectClient(final String subsystem,
            final TransportChannelListener listener, final TcpClientGrouping connectParams,
            final SshClientGrouping clientParams) throws UnsupportedConfigurationException {
        return SSHClient.of(ioServiceFactory, group, subsystem, listener, clientParams)
            .connect(newBootstrap(), connectParams);
    }

    public @NonNull ListenableFuture<SSHClient> listenClient(final String subsystem,
            final TransportChannelListener listener, final TcpServerGrouping listenParams,
            final SshClientGrouping clientParams) throws UnsupportedConfigurationException {
        return SSHClient.of(ioServiceFactory, group, subsystem, listener, clientParams)
            .listen(newServerBootstrap(), listenParams);
    }

    public @NonNull ListenableFuture<SSHServer> connectServer(final TransportChannelListener listener,
            final SubsystemFactory subsystemFactory, final TcpClientGrouping connectParams,
            final SshServerGrouping serverParams) throws UnsupportedConfigurationException {
        return SSHServer.of(ioServiceFactory, group, listener, subsystemFactory, requireNonNull(serverParams), null)
            .connect(newBootstrap(), connectParams);
    }

    public @NonNull ListenableFuture<SSHServer> listenServer(final TransportChannelListener listener,
            final SubsystemFactory subsystemFactory, final TcpServerGrouping connectParams,
            final SshServerGrouping serverParams) throws UnsupportedConfigurationException {
        return listenServer(listener, subsystemFactory, connectParams, requireNonNull(serverParams), null);
    }

    /**
     * Builds and starts SSH Server.
     *
     * @param listener server channel listener, required
     * @param subsystemFactory A {@link SubsystemFactory} for the hosted subsystem
     * @param listenParams TCP transport configuration, required
     * @param serverParams SSH overlay configuration, optional if configurator is defined, required otherwise
     * @param configurator server factory manager configurator, optional if serverParams is defined, required otherwise
     * @return server instance as listenable future
     * @throws UnsupportedConfigurationException if any of configurations is invalid or incomplete
     * @throws NullPointerException if any of required parameters is null
     * @throws IllegalArgumentException if both configurator and serverParams are null
     */
    public @NonNull ListenableFuture<SSHServer> listenServer(final TransportChannelListener listener,
            final SubsystemFactory subsystemFactory, final TcpServerGrouping listenParams,
            final SshServerGrouping serverParams, final ServerFactoryManagerConfigurator configurator)
                    throws UnsupportedConfigurationException {
        checkArgument(serverParams != null || configurator != null,
            "Neither server parameters nor factory configurator is defined");
        return SSHServer.of(ioServiceFactory, group, listener, subsystemFactory, serverParams, configurator)
            .listen(newServerBootstrap(), listenParams);
    }

    /**
     * Create a new {@link Bootstrap} based on this factory's {@link EventLoopGroup}s.
     *
     * @return A new {@link Bootstrap}
     */
    public @NonNull Bootstrap newBootstrap() {
        return NettyTransportSupport.newBootstrap().group(group);
    }

    /**
     * Create a new {@link ServerBootstrap} based on this factory's {@link EventLoopGroup}s.
     *
     * @return A new {@link ServerBootstrap}
     */
    public @NonNull ServerBootstrap newServerBootstrap() {
        return NettyTransportSupport.newServerBootstrap().group(parentGroup != null ? parentGroup : group, group);
    }

    @Override
    public void close() {
        if (parentGroup != null) {
            parentGroup.shutdownGracefully();
        }
        group.shutdownGracefully();
    }
}
