/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
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
    private final String subsystem;

    private SSHClient(final String subsystem, final TransportChannelListener listener,
            final TransportSshClient sshClient) {
        super(listener, sshClient, sshClient.getSessionFactory());
        this.subsystem = requireNonNull(subsystem);
    }

    static SSHClient of(final NettyIoServiceFactoryFactory ioServiceFactory, final EventLoopGroup group,
            final String subsystem, final TransportChannelListener listener, final SshClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        return new SSHClient(subsystem, listener, new TransportSshClient.Builder(ioServiceFactory, group)
            .transportParams(clientParams.getTransportParams())
            .keepAlives(clientParams.getKeepalives())
            .clientIdentity(clientParams.getClientIdentity())
            .serverAuthentication(clientParams.getServerAuthentication())
            .buildChecked());
    }

    @NonNull ListenableFuture<SSHClient> connect(final Bootstrap bootstrap, final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        return transformUnderlay(this, TCPClient.connect(asListener(), bootstrap, connectParams));
    }

    @NonNull ListenableFuture<SSHClient> listen(final ServerBootstrap bootstrap, final TcpServerGrouping listenParams)
            throws UnsupportedConfigurationException {
        return transformUnderlay(this, TCPServer.listen(asListener(), bootstrap, listenParams));
    }

    @Override
    void onKeyEstablished(final Session session) throws IOException {
        // server key is accepted, trigger authentication flow
        cast(session).auth().addListener(future -> {
            if (!future.isSuccess()) {
                deleteSession(session);
            }
        });
    }

    @Override
    void onAuthenticated(final Session session) throws IOException {
        final var clientSession = cast(session);

        final var underlay = underlayOf(clientSession);
        if (underlay == null) {
            throw new IOException("Cannot find underlay for " + session);
        }

        final var channel = clientSession.createSubsystemChannel(subsystem);
        channel.onClose(() -> clientSession.close(true));
        channel.open(underlay).addListener(future -> onSubsystemOpenComplete(future, clientSession));
    }

    private void onSubsystemOpenComplete(final OpenFuture future, final TransportClientSession session) {
        if (future.isOpened()) {
            completeUnderlay(session, underlay -> addTransportChannel(new SSHTransportChannel(underlay)));
        } else {
            deleteSession(session);
        }
    }

    private static TransportClientSession cast(final Session session) throws IOException {
        if (session instanceof TransportClientSession clientSession) {
            return clientSession;
        }
        throw new IOException("Unexpected session " + session);
    }
}
