/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.shaded.sshd.server.session.SessionFactory;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;

/**
 * A {@link TransportStack} acting as an SSH server.
 */
public final class SSHServer extends SSHTransportStack {
    private final ServerFactoryManager serverFactoryManager;
    private final SessionFactory serverSessionFactory;

    private SSHServer(final TransportChannelListener listener, final ServerFactoryManager serverFactoryManager) {
        super(listener);
        this.serverFactoryManager = requireNonNull(serverFactoryManager);
        this.serverFactoryManager.addSessionListener(new UserAuthSessionListener(sessionAuthHandlers, sessions));
        serverSessionFactory = new SessionFactory(serverFactoryManager);
        ioService = new SshIoService(this.serverFactoryManager,
                new DefaultChannelGroup("sshd-server-channels", GlobalEventExecutor.INSTANCE),
                serverSessionFactory);
    }

    @Override
    IoHandler getSessionFactory() {
        return serverSessionFactory;
    }

    public static @NonNull ListenableFuture<SSHServer> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams, final SshServerGrouping serverParams)
                throws UnsupportedConfigurationException {
        final var server = newServer(listener, requireNonNull(serverParams), null);
        return transformUnderlay(server, TCPClient.connect(server.asListener(), bootstrap, connectParams));
    }

    public static @NonNull ListenableFuture<SSHServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping connectParams,
            final SshServerGrouping serverParams) throws UnsupportedConfigurationException {
        return listen(listener, bootstrap, connectParams, requireNonNull(serverParams), null);
    }

    /**
     * Builds and starts SSH Server.
     *
     * @param listener server channel listener, required
     * @param bootstrap server bootstrap instance, required
     * @param connectParams tcp transport configuration, required
     * @param serverParams ssh overlay configuration, optional if configurator is defined, required otherwise
     * @param configurator server factory manager configurator, optional if serverParams is defined, required otherwise
     * @return server instance as listenable future
     * @throws UnsupportedConfigurationException if any of configurations is invalid or incomplete
     * @throws NullPointerException if any of required parameters is null
     * @throws IllegalArgumentException if both configurator and serverParams are null
     */
    public static @NonNull ListenableFuture<SSHServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping connectParams,
            final SshServerGrouping serverParams, final ServerFactoryManagerConfigurator configurator)
                throws UnsupportedConfigurationException {
        checkArgument(serverParams != null || configurator != null,
            "Neither server parameters nor factory configurator is defined");
        final var server = newServer(listener, serverParams, configurator);
        return transformUnderlay(server, TCPServer.listen(server.asListener(), bootstrap, connectParams));
    }

    private static SSHServer newServer(final TransportChannelListener listener, final SshServerGrouping serverParams,
            final ServerFactoryManagerConfigurator configurator) throws UnsupportedConfigurationException {
        return new SSHServer(listener, new TransportSshServer.Builder()
            .serverParams(serverParams)
            .configurator(configurator)
            .buildChecked());
    }
}