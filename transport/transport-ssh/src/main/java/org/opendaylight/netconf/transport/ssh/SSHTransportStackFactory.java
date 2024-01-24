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
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev231228.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev231228.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev231228.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev231228.TcpServerGrouping;

/**
 * A {@link BootstrapFactory} additionally capable of instantiating {@link SSHClient}s and {@link SSHServer}s.
 */
public final class SSHTransportStackFactory extends BootstrapFactory {
    private final NettyIoServiceFactoryFactory ioServiceFactory = new NettyIoServiceFactoryFactory(group);

    public SSHTransportStackFactory(final @NonNull String groupName, final int groupThreads) {
        super(groupName, groupThreads);
    }

    public SSHTransportStackFactory(final @NonNull String groupName, final int groupThreads,
            final @NonNull String parentGroupName, final int parentGroupThreads) {
        super(groupName, groupThreads, parentGroupName, parentGroupThreads);
    }

    public @NonNull ListenableFuture<SSHClient> connectClient(final String subsystem,
            final TransportChannelListener listener, final TcpClientGrouping connectParams,
            final SshClientGrouping clientParams) throws UnsupportedConfigurationException {
        return SSHClient.of(ioServiceFactory, group, subsystem, listener, clientParams, null)
            .connect(newBootstrap(), connectParams);
    }

    /** Builds the SSH Client and initiates connection.
     *
     * @param subsystem bound subsystem name
     * @param listener client channel listener, required
     * @param connectParams TCP transport configuration addressing server to connect, required
     * @param clientParams SSH overlay configuration, required, should contain username
     * @param configurator client factory manager configurator, optional
     * @return a future producing {@link SSHClient}
     * @throws UnsupportedConfigurationException if any of configurations is invalid or incomplete
     * @throws NullPointerException if any of required parameters is null
     */
    public @NonNull ListenableFuture<SSHClient> connectClient(final String subsystem,
            final TransportChannelListener listener, final TcpClientGrouping connectParams,
            final SshClientGrouping clientParams, final ClientFactoryManagerConfigurator configurator)
            throws UnsupportedConfigurationException {
        return SSHClient.of(ioServiceFactory, group, subsystem, listener, clientParams, configurator)
            .connect(newBootstrap(), connectParams);
    }

    public @NonNull ListenableFuture<SSHClient> listenClient(final String subsystem,
            final TransportChannelListener listener, final TcpServerGrouping listenParams,
            final SshClientGrouping clientParams) throws UnsupportedConfigurationException {
        return SSHClient.of(ioServiceFactory, group, subsystem, listener, clientParams, null)
            .listen(newServerBootstrap(), listenParams);
    }

    /**
     * Builds and starts Call-Home SSH Client.
     *
     * @param subsystem bound subsystem name
     * @param listener client channel listener, required
     * @param listenParams TCP transport configuration addressing inbound connection, required
     * @param clientParams SSH overlay configuration, required, should contain username
     * @param configurator client factory manager configurator, optional
     * @return a future producing {@link SSHClient}
     * @throws UnsupportedConfigurationException if any of configurations is invalid or incomplete
     * @throws NullPointerException if any of required parameters is null
     */
    public @NonNull ListenableFuture<SSHClient> listenClient(final String subsystem,
            final TransportChannelListener listener, final TcpServerGrouping listenParams,
            final SshClientGrouping clientParams, final ClientFactoryManagerConfigurator configurator)
            throws UnsupportedConfigurationException {
        return SSHClient.of(ioServiceFactory, group, subsystem, listener, clientParams, configurator)
            .listen(newServerBootstrap(), listenParams);
    }

    public @NonNull ListenableFuture<SSHServer> connectServer(final String subsystem,
            final TransportChannelListener listener, final TcpClientGrouping connectParams,
            final SshServerGrouping serverParams) throws UnsupportedConfigurationException {
        return SSHServer.of(ioServiceFactory, group, subsystem, listener, requireNonNull(serverParams), null)
            .connect(newBootstrap(), connectParams);
    }

    /**
     * Builds and starts a Call-Home SSH Server, initiates connection to client.
     *
     * @param subsystem bound subsystem name
     * @param listener server channel listener, required
     * @param connectParams TCP transport configuration addressing client to be connected, required
     * @param serverParams SSH overlay configuration, optional if configurator is defined, required otherwise
     * @param configurator server factory manager configurator, optional if serverParams is defined, required otherwise
     * @return a future producing {@link SSHServer}
     * @throws UnsupportedConfigurationException if any of configurations is invalid or incomplete
     * @throws NullPointerException if any of required parameters is null
     * @throws IllegalArgumentException if both configurator and serverParams are null
     */
    public @NonNull ListenableFuture<SSHServer> connectServer(final String subsystem,
            final TransportChannelListener listener, final TcpClientGrouping connectParams,
            final SshServerGrouping serverParams, final ServerFactoryManagerConfigurator configurator)
            throws UnsupportedConfigurationException {
        checkArgument(serverParams != null || configurator != null,
            "Neither server parameters nor factory configurator is defined");
        return SSHServer.of(ioServiceFactory, group, subsystem, listener, serverParams, configurator)
            .connect(newBootstrap(), connectParams);
    }

    public @NonNull ListenableFuture<SSHServer> listenServer(final String subsystem,
            final TransportChannelListener listener, final TcpServerGrouping connectParams,
            final SshServerGrouping serverParams) throws UnsupportedConfigurationException {
        return listenServer(subsystem, listener, connectParams, requireNonNull(serverParams), null);
    }

    /**
     * Builds and starts SSH Server.
     *
     * @param listener server channel listener, required
     * @param subsystem bound subsystem name
     * @param listenParams TCP transport configuration, required
     * @param serverParams SSH overlay configuration, optional if configurator is defined, required otherwise
     * @param configurator server factory manager configurator, optional if serverParams is defined, required otherwise
     * @return a future producing {@link SSHServer}
     * @throws UnsupportedConfigurationException if any of configurations is invalid or incomplete
     * @throws NullPointerException if any of required parameters is null
     * @throws IllegalArgumentException if both configurator and serverParams are null
     */
    public @NonNull ListenableFuture<SSHServer> listenServer(final String subsystem,
            final TransportChannelListener listener, final TcpServerGrouping listenParams,
            final SshServerGrouping serverParams, final ServerFactoryManagerConfigurator configurator)
                throws UnsupportedConfigurationException {
        checkArgument(serverParams != null || configurator != null,
            "Neither server parameters nor factory configurator is defined");
        return SSHServer.of(ioServiceFactory, group, subsystem, listener, serverParams, configurator)
            .listen(newServerBootstrap(), listenParams);
    }
}
