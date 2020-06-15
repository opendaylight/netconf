/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;

public class TlsClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> {
    public static final String CHANNEL_ACTIVE_SENTRY = "channelActiveSentry";

    private final SslConfigurationProvider sslConfigurationProvider;
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    public TlsClientChannelInitializer(final SslConfigurationProvider sslConfigurationProvider,
                                final NetconfClientSessionNegotiatorFactory negotiatorFactory,
                                final NetconfClientSessionListener sessionListener) {
        this.sslConfigurationProvider = sslConfigurationProvider;
        this.negotiatorFactory = negotiatorFactory;
        this.sessionListener = sessionListener;
    }

    @Override
    public void initialize(Channel ch, Promise<NetconfClientSession> promise) {
        // When ssl handshake fails due to the certificate mismatch, the connection will try again,
        // then we have a chance to create a new SslHandler using the latest certificates with the
        // help of the sentry. We will replace the sentry with the new SslHandler once the channel
        // is active.
        ch.pipeline().addFirst(CHANNEL_ACTIVE_SENTRY, new TlsClientChannelInitializer.ChannelActiveSentry(sslConfigurationProvider));
        super.initialize(ch, promise);
    }

    @Override
    protected void initializeSessionNegotiator(Channel ch, Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, AbstractChannelInitializer.NETCONF_SESSION_NEGOTIATOR,
            negotiatorFactory.getSessionNegotiator(() -> sessionListener, ch, promise));
    }

    private static final class ChannelActiveSentry extends ChannelInboundHandlerAdapter {
        private final SslConfigurationProvider sslConfigurationProvider;

        ChannelActiveSentry(final SslConfigurationProvider sslConfigurationProvider) {
            this.sslConfigurationProvider = sslConfigurationProvider;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.pipeline().replace(this, "sslHandler", sslConfigurationProvider.getSslHandler())
                .fireChannelActive();
        }
    }
}
