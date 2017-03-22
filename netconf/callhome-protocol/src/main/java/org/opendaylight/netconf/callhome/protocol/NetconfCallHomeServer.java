/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.client.ServerKeyVerifier;
import org.apache.sshd.client.SessionFactory;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.SessionListener;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoServiceFactory;
import org.apache.sshd.common.io.mina.MinaServiceFactory;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactory;
import org.opendaylight.netconf.callhome.protocol.CallHomeSessionContext.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfCallHomeServer implements AutoCloseable, ServerKeyVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeServer.class);

    private final IoAcceptor acceptor;
    private final SshClient client;
    private final CallHomeAuthorizationProvider authProvider;
    private final CallHomeSessionContext.Factory sessionFactory;
    private final InetSocketAddress bindAddress;

    NetconfCallHomeServer(SshClient sshClient, CallHomeAuthorizationProvider authProvider, Factory factory,
            InetSocketAddress socketAddress) {
        this.client = Preconditions.checkNotNull(sshClient);
        this.authProvider = Preconditions.checkNotNull(authProvider);
        this.sessionFactory = Preconditions.checkNotNull(factory);
        this.bindAddress = socketAddress;

        sshClient.setServerKeyVerifier(this);

        SessionFactory clientSessions = new SessionFactory();
        clientSessions.setClient(sshClient);
        clientSessions.addListener(createSessionListener());

        IoServiceFactory minaFactory = createServiceFactory(sshClient);
        this.acceptor = minaFactory.createAcceptor(clientSessions);
    }

    IoServiceFactory createServiceFactory(SshClient sshClient) {
        try {
            return createMinaServiceFactory(sshClient);
        } catch (NoClassDefFoundError e) {
            LOG.warn("Mina is not available, defaulting to NIO.");
            return new Nio2ServiceFactory(sshClient);
        }
    }


    protected IoServiceFactory createMinaServiceFactory(SshClient sshClient) {
        return new MinaServiceFactory(sshClient);
    }

    SessionListener createSessionListener() {
        return new SessionListener() {

            @Override
            public void sessionEvent(Session session, Event event) {
                ClientSession cSession = (ClientSession) session;
                LOG.debug("SSH session {} event {}", session, event);
                switch (event) {
                    case KeyEstablished:
                        doAuth(cSession);
                        break;
                    case Authenticated:
                        doPostAuth(cSession);
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void sessionCreated(Session session) {
                LOG.debug("SSH session {} created", session);
            }

            @Override
            public void sessionClosed(Session session) {
                CallHomeSessionContext ctx = CallHomeSessionContext.getFrom((ClientSession) session);
                if(ctx != null) {
                    ctx.removeSelf();
                }
                LOG.debug("SSH Session {} closed", session);
            }
        };
    }

    private void doPostAuth(final ClientSession cSession) {
        CallHomeSessionContext.getFrom(cSession).openNetconfChannel();
    }

    private void doAuth(final ClientSession cSession) {
        try {
            final AuthFuture authFuture = CallHomeSessionContext.getFrom(cSession).authorize();
            authFuture.addListener(newAuthSshFutureListener(cSession));
        } catch (IOException e) {
            LOG.error("Failed to authorize session {}", cSession, e);
        }
    }

    SshFutureListener<AuthFuture> newAuthSshFutureListener(final ClientSession cSession) {
        return new SshFutureListener<AuthFuture>() {
            @Override
            public void operationComplete(AuthFuture authFuture) {
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

            private void onFailure(Throwable throwable) {
                LOG.error("Failed to authorize session {}", cSession, throwable);
                cSession.close(true);
            }

            private void onCanceled() {
                LOG.warn("Authorize cancelled");
                cSession.close(true);
            }
        };
    }

    @Override
    public boolean verifyServerKey(ClientSession sshClientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        final CallHomeAuthorization authorization = authProvider.provideAuth(remoteAddress, serverKey);
        // server is not authorized
        if (!authorization.isServerAllowed()) {
            LOG.info("Incoming session {} was rejected by Authorization Provider.",sshClientSession);
            return false;
        }
        CallHomeSessionContext session = sessionFactory.createIfNotExists(sshClientSession, authorization, remoteAddress);
        // Session was created, session with same name does not exists
        if(session != null) {
            return true;
        }
        // Session was not created, session with same name exists
        LOG.info("Incoming session {} was rejected. Session with same name {} is already active.",sshClientSession,authorization.getSessionName());
        return false;
    }

    public void bind() throws IOException {
        try {
            client.start();
            acceptor.bind(bindAddress);
        } catch (IOException e) {
            LOG.error("Unable to start NETCONF CallHome Service", e);
            throw e;
        }
    }


    @Override
    public void close() throws Exception {
        acceptor.close(true);
    }
}
