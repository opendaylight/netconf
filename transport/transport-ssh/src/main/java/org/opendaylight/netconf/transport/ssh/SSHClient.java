/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TransportStack} acting as an SSH client.
 */
public final class SSHClient extends SSHTransportStack {
    private static final Logger LOG = LoggerFactory.getLogger(SSHClient.class);

    private final String subsystem;

    private SSHClient(final String subsystem, final TransportChannelListener listener,
            final TransportSshClient sshClient) {
        super(listener, sshClient, sshClient.getSessionFactory());
        // Mirrors check in ChannelSubsystem's constructor
        if (subsystem.isBlank()) {
            throw new IllegalArgumentException("Blank subsystem");
        }
        this.subsystem = subsystem;
    }

    static SSHClient of(final NettyIoServiceFactoryFactory ioServiceFactory,
            final ScheduledExecutorService executorService, final String subsystem,
            final TransportChannelListener listener, final SshClientGrouping clientParams,
            final ClientFactoryManagerConfigurator configurator) throws UnsupportedConfigurationException {
        return new SSHClient(subsystem, listener, new TransportSshClient.Builder(ioServiceFactory, executorService)
            .transportParams(clientParams.getTransportParams())
            .keepAlives(clientParams.getKeepalives())
            .clientIdentity(clientParams.getClientIdentity())
            .serverAuthentication(clientParams.getServerAuthentication())
            .configurator(configurator)
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
        final var sessionId = sessionId(session);
        LOG.debug("Authenticating session {}", sessionId);
        cast(session).auth().addListener(future -> onAuthComplete(future, sessionId));
    }

    private void onAuthComplete(final AuthFuture future, final Long sessionId) {
        if (!future.isSuccess()) {
            LOG.info("Session {} authentication failed", sessionId);
            deleteSession(sessionId);
        } else {
            LOG.debug("Session {} authenticated", sessionId);
        }
    }

    @Override
    void onAuthenticated(final Session session) throws IOException {
        final var sessionId = sessionId(session);
        LOG.debug("Opening \"{}\" subsystem on session {}", subsystem, sessionId);

        final var underlay = getUnderlayOf(sessionId);
        final var clientSession = cast(session);
        final var channel = clientSession.createSubsystemChannel(subsystem);
        channel.onClose(() -> clientSession.close(true));
        Futures.addCallback(channel.open(underlay), new FutureCallback<>() {
            @Override
            public void onSuccess(final ChannelHandlerContext result) {
                LOG.debug("Opened \"{}\" subsystem on session {}", subsystem, sessionId);
                transportEstablished(sessionId, result);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.error("Failed to open \"{}\" subsystem on session {}", subsystem, sessionId, cause);
                deleteSession(sessionId);
            }
        }, MoreExecutors.directExecutor());
    }

    private static TransportClientSession cast(final Session session) throws IOException {
        return TransportUtils.checkCast(TransportClientSession.class, session);
    }
}
