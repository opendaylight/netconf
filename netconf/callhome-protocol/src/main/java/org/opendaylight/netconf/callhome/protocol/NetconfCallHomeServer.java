/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import org.opendaylight.netconf.callhome.protocol.CallHomeSessionContext.Factory;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.client.keyverifier.ServerKeyVerifier;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.client.session.SessionFactory;
import org.opendaylight.netconf.shaded.sshd.common.future.SshFutureListener;
import org.opendaylight.netconf.shaded.sshd.common.io.IoAcceptor;
import org.opendaylight.netconf.shaded.sshd.common.io.IoServiceFactory;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfCallHomeServer implements AutoCloseable, ServerKeyVerifier {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeServer.class);

    private final CallHomeAuthorizationProvider authProvider;
    private final IoServiceFactory serviceFactory;
    private final InetSocketAddress bindAddress;
    private final StatusRecorder recorder;
    private final Factory sessionFactory;
    private final IoAcceptor acceptor;
    private final SshClient client;

    NetconfCallHomeServer(final SshClient sshClient, final CallHomeAuthorizationProvider authProvider,
            final Factory factory, final InetSocketAddress socketAddress, final StatusRecorder recorder) {
        this(sshClient, authProvider, factory, socketAddress, recorder,
            new NettyIoServiceFactory(factory.getNettyGroup()));
    }

    @VisibleForTesting
    NetconfCallHomeServer(final SshClient sshClient, final CallHomeAuthorizationProvider authProvider,
            final Factory factory, final InetSocketAddress socketAddress, final StatusRecorder recorder,
            final IoServiceFactory serviceFactory) {
        client = requireNonNull(sshClient);
        this.authProvider = requireNonNull(authProvider);
        sessionFactory = requireNonNull(factory);
        bindAddress = socketAddress;
        this.recorder = recorder;
        this.serviceFactory = requireNonNull(serviceFactory);

        sshClient.setServerKeyVerifier(this);
        sshClient.addSessionListener(createSessionListener());

        acceptor = serviceFactory.createAcceptor(new SessionFactory(sshClient));
    }

    @VisibleForTesting
    SshClient getClient() {
        return client;
    }

    SessionListener createSessionListener() {
        return new SessionListener() {
            @Override
            public void sessionEvent(final Session session, final Event event) {
                ClientSession clientSession = (ClientSession) session;
                LOG.debug("SSH session {} event {}", session, event);
                switch (event) {
                    case KeyEstablished:
                        // Case of key re-exchange - if session is once authenticated, it does not need to be made again
                        if (!clientSession.isAuthenticated()) {
                            doAuth(clientSession);
                        }
                        break;
                    case Authenticated:
                        CallHomeSessionContext.getFrom(clientSession).openNetconfChannel();
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void sessionCreated(final Session session) {
                LOG.debug("SSH session {} created", session);
            }

            @Override
            public void sessionClosed(final Session session) {
                CallHomeSessionContext ctx = CallHomeSessionContext.getFrom((ClientSession) session);
                if (ctx != null) {
                    ctx.removeSelf();
                }
                LOG.debug("SSH Session {} closed", session);
            }

            private void doAuth(final ClientSession session) {
                try {
                    final AuthFuture authFuture = CallHomeSessionContext.getFrom(session).authorize();
                    authFuture.addListener(newAuthSshFutureListener(session));
                } catch (IOException e) {
                    LOG.error("Failed to authorize session {}", session, e);
                }
            }
        };
    }

    private SshFutureListener<AuthFuture> newAuthSshFutureListener(final ClientSession session) {
        final PublicKey serverKey = session.getServerKey();

        return new SshFutureListener<>() {
            @Override
            public void operationComplete(final AuthFuture authFuture) {
                if (authFuture.isSuccess()) {
                    onSuccess();
                } else if (authFuture.isFailure()) {
                    onFailure(authFuture.getException());
                } else if (authFuture.isCanceled()) {
                    onCanceled();
                }
                authFuture.removeListener(this);
            }

            private void onSuccess() {
                LOG.debug("Authorize success");
            }

            private void onFailure(final Throwable throwable) {
                LOG.error("Authorize failed for session {}", session, throwable);
                recorder.reportFailedAuth(serverKey);
                session.close(true);
            }

            private void onCanceled() {
                LOG.warn("Authorize cancelled");
                session.close(true);
            }
        };
    }

    @Override
    public boolean verifyServerKey(final ClientSession sshClientSession, final SocketAddress remoteAddress,
            final PublicKey serverKey) {
        final CallHomeAuthorization authorization = authProvider.provideAuth(remoteAddress, serverKey);
        if (!authorization.isServerAllowed()) {
            // server is not authorized
            LOG.info("Incoming session {} was rejected by Authorization Provider.", sshClientSession);
            return false;
        }

        if (sessionFactory.createIfNotExists(sshClientSession, authorization) == null) {
            // Session was not created, session with same name exists
            LOG.info("Incoming session {} was rejected. Session with same name {} is already active.", sshClientSession,
                authorization.getSessionName());
            return false;
        }

        // Session was created, session with same name does not exist
        LOG.debug("Incoming session {} was successfully verified.", sshClientSession);
        return true;
    }

    public void bind() throws IOException {
        try {
            client.start();
            acceptor.bind(bindAddress);
        } catch (IOException e) {
            LOG.error("Unable to start NETCONF CallHome Service on {}", bindAddress, e);
            throw e;
        }
    }

    @Override
    public void close() {
        acceptor.close(true);
        serviceFactory.close(true);
    }
}
