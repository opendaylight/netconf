/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSessionImpl;
import org.opendaylight.netconf.shaded.sshd.client.session.SessionFactory;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;

/**
 * A {@link TransportStack} acting as an SSH client.
 */
public final class SSHClient extends SSHTransportStack {
    private final ClientFactoryManager clientFactoryManager;
    private final SessionFactory sessionFactory;

    private SSHClient(final TransportChannelListener listener, final ClientFactoryManager clientFactoryManager,
            final String username) {
        super(listener);
        this.clientFactoryManager = clientFactoryManager;
        this.clientFactoryManager.addSessionListener(new UserAuthSessionListener(sessionAuthHandlers, sessions));
        sessionFactory = new SessionFactory(clientFactoryManager) {
            @Override
            protected ClientSessionImpl setupSession(final ClientSessionImpl session) {
                session.setUsername(username);
                return session;
            }
        };
        ioService = new SshIoService(this.clientFactoryManager,
                new DefaultChannelGroup("sshd-client-channels", GlobalEventExecutor.INSTANCE),
                sessionFactory);
    }

    @Override
    IoHandler getSessionFactory() {
        return sessionFactory;
    }

    public static @NonNull ListenableFuture<SSHClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams,
            final SshClientGrouping clientParams) throws UnsupportedConfigurationException {
        final var factoryMgr = newFactoryManager(clientParams);
        final var sshClient = new SSHClient(listener, factoryMgr, getUsername(clientParams));
        return transformUnderlay(sshClient, TCPClient.connect(sshClient.asListener(), bootstrap, connectParams));
    }

    public static @NonNull ListenableFuture<SSHClient> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final SshClientGrouping clientParams)
            throws UnsupportedConfigurationException {
        final var factoryMgr = newFactoryManager(clientParams);
        final var sshClient = new SSHClient(listener, factoryMgr, getUsername(clientParams));
        return transformUnderlay(sshClient, TCPServer.listen(sshClient.asListener(), bootstrap, listenParams));
    }

    private static String getUsername(final SshClientGrouping clientParams) {
        final var clientIdentity = clientParams.getClientIdentity();
        return clientIdentity == null ? "" : clientIdentity.getUsername();
    }

    private static ClientFactoryManager newFactoryManager(final SshClientGrouping parameters)
            throws UnsupportedConfigurationException {
        return new TransportSshClient.Builder()
            .transportParams(parameters.getTransportParams())
            .keepAlives(parameters.getKeepalives())
            .clientIdentity(parameters.getClientIdentity())
            .serverAuthentication(parameters.getServerAuthentication())
            .buildChecked();
    }
}