/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
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
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev241010.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev241010.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.TcpServerGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TransportStack} acting as an SSH server.
 */
public final class SSHServer extends SSHTransportStack {
    private static final Logger LOG = LoggerFactory.getLogger(SSHServer.class);

    private final String subsystem;

    private SSHServer(final String subsystem, final TransportChannelListener<? super SSHTransportChannel> listener,
            final TransportSshServer sshServer) {
        super(listener, sshServer, sshServer.getSessionFactory());
        this.subsystem = requireNonNull(subsystem);
    }

    static SSHServer of(final NettyIoServiceFactoryFactory ioServiceFactory,
            final ScheduledExecutorService executorService, final String subsystem,
            final TransportChannelListener<? super SSHTransportChannel> listener, final SshServerGrouping serverParams,
            final ServerFactoryManagerConfigurator configurator) throws UnsupportedConfigurationException {
        return new SSHServer(subsystem, listener,
            new TransportSshServer.Builder(ioServiceFactory, executorService)
                .serverParams(serverParams)
                .configurator(configurator)
                .buildChecked());
    }

    @NonNull ListenableFuture<SSHServer> connect(final Bootstrap bootstrap, final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        return connect(bootstrap, connectParams, asListener());
    }

    @VisibleForTesting
    @NonNull ListenableFuture<SSHServer> connect(final Bootstrap bootstrap, final TcpClientGrouping connectParams,
            TransportChannelListener<TransportChannel> listen) throws UnsupportedConfigurationException {
        return transformUnderlay(this, TCPClient.connect(listen, bootstrap, connectParams));
    }

    @NonNull ListenableFuture<SSHServer> listen(final ServerBootstrap bootstrap, final TcpServerGrouping connectParams)
            throws UnsupportedConfigurationException {
        return transformUnderlay(this, TCPServer.listen(asListener(), bootstrap, connectParams));
    }

    @Override
    void onKeyEstablished(final Session session) {
        // No-op
    }

    @Override
    void onAuthenticated(final Session session) throws IOException {
        final var sessionId = sessionId(session);
        LOG.debug("Awaiting \"{}\" subsystem on session {}", subsystem, sessionId);

        Futures.addCallback(cast(session).attachUnderlay(subsystem, getUnderlayOf(sessionId)), new FutureCallback<>() {
            @Override
            public void onSuccess(final ChannelHandlerContext result) {
                LOG.debug("Established \"{}\" subsystem on session {}", subsystem, sessionId);
                // Note: we re-validating the underlay ... we may need to refactor state management to make this
                //       non-awkward
                transportEstablished(sessionId, result);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Binding to \"{}\" subsystem on session {} failed", subsystem, sessionId, cause);
                transportFailed(sessionId, cause);
            }
        }, MoreExecutors.directExecutor());
    }

    private static TransportServerSession cast(final Session session) throws IOException {
        return TransportUtils.checkCast(TransportServerSession.class, session);
    }
}
