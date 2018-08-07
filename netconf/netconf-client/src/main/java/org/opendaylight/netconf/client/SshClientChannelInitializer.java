/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandler;

final class SshClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> {

    private final AuthenticationHandler authenticationHandler;
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    SshClientChannelInitializer(final AuthenticationHandler authHandler,
                                final NetconfClientSessionNegotiatorFactory negotiatorFactory,
                                final NetconfClientSessionListener sessionListener) {
        this.authenticationHandler = authHandler;
        this.negotiatorFactory = negotiatorFactory;
        this.sessionListener = sessionListener;
    }

    @Override
    public void initialize(final Channel ch, final Promise<NetconfClientSession> promise) {
        // ssh handler has to be the first handler in pipeline
        ch.pipeline().addFirst(AsyncSshHandler.createForNetconfSubsystem(authenticationHandler, promise));
        super.initialize(ch, promise);
        initializeIdleStateHandler(ch);
    }

    @Override
    protected void initializeSessionNegotiator(final Channel ch,
                                               final Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, AbstractChannelInitializer.NETCONF_SESSION_NEGOTIATOR,
                negotiatorFactory.getSessionNegotiator(() -> sessionListener, ch, promise));
    }

    protected void initializeIdleStateHandler(Channel ch) {
        ch.pipeline().addLast(new IdleHandler(sessionListener, 2,2,2));
    }

    // This handler is mostly used to detect an idle state (eg for 1 min)
    // and provide an indication to kick the keepalive mechanism into action.
    private static final class IdleHandler<S> extends IdleStateHandler {
        NetconfClientSessionListener sessionListener;

        IdleHandler(NetconfClientSessionListener sessionListener, int readerIdleTimeSeconds,
                           int writerIdleTimeSeconds, int allIdleTimeSeconds) {
            super(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds);
            this.sessionListener = sessionListener;
        }

        @Override
        protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
            sessionListener.onSessionIdle();
        }
    }

}
