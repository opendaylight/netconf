/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;

final class SshClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> {
    private final AuthenticationHandler authenticationHandler;
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;
    private final NetconfSshClient sshClient;
    private final String nodeId;

    SshClientChannelInitializer(final AuthenticationHandler authHandler,
            final NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final NetconfClientSessionListener sessionListener, @Nullable final NetconfSshClient sshClient,
            final String nodeId) {
        this.authenticationHandler = authHandler;
        this.negotiatorFactory = negotiatorFactory;
        this.sessionListener = sessionListener;
        this.sshClient = sshClient;
        this.nodeId = nodeId;
    }

    SshClientChannelInitializer(final AuthenticationHandler authHandler,
            final NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final NetconfClientSessionListener sessionListener, final String nodeId) {
        this(authHandler, negotiatorFactory, sessionListener, null, nodeId);
    }

    @Override
    public void initialize(final Channel ch, final Promise<NetconfClientSession> promise) {
        // ssh handler has to be the first handler in pipeline
        ch.pipeline().addFirst(AsyncSshHandler.createForNetconfSubsystem(
                authenticationHandler, promise, sshClient, nodeId));
        super.initialize(ch, promise);
    }

    @Override
    protected void initializeSessionNegotiator(final Channel ch,
                                               final Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, AbstractChannelInitializer.NETCONF_SESSION_NEGOTIATOR,
                negotiatorFactory.getSessionNegotiator(() -> sessionListener, ch, promise));
        ch.config().setConnectTimeoutMillis((int)negotiatorFactory.getConnectionTimeoutMillis());
    }
}
