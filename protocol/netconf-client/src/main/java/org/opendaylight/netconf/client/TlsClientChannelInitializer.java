/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Promise;
import java.util.Set;

public final class TlsClientChannelInitializer extends AbstractClientChannelInitializer {
    public static final String CHANNEL_ACTIVE_SENTRY = "channelActiveSentry";

    private final SslHandlerFactory sslHandlerFactory;
    private final Set<String> privateKeyIds;

    public TlsClientChannelInitializer(final SslHandlerFactory sslHandlerFactory,
                                       final Set<String> privateKeyIds,
                                       final NetconfClientSessionNegotiatorFactory negotiatorFactory,
                                       final NetconfClientSessionListener sessionListener) {
        super(negotiatorFactory, sessionListener);
        this.sslHandlerFactory = sslHandlerFactory;
        this.privateKeyIds = privateKeyIds;
    }

    @Override
    public void initialize(final Channel ch, final Promise<NetconfClientSession> promise) {
        // When ssl handshake fails due to the certificate mismatch, the connection will try again,
        // then we have a chance to create a new SslHandler using the latest certificates with the
        // help of the sentry. We will replace the sentry with the new SslHandler once the channel
        // is active.
        ch.pipeline().addFirst(CHANNEL_ACTIVE_SENTRY, new ChannelActiveSentry(sslHandlerFactory, privateKeyIds));
        super.initialize(ch, promise);
    }

    private static final class ChannelActiveSentry extends ChannelInboundHandlerAdapter {
        private final SslHandlerFactory sslHandlerFactory;
        private final Set<String> privateKeyIds;

        ChannelActiveSentry(final SslHandlerFactory sslHandlerFactory, final Set<String> privateKeyIds) {
            this.sslHandlerFactory = sslHandlerFactory;
            this.privateKeyIds = privateKeyIds;
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            final var sslHandler = sslHandlerFactory.createSslHandler(privateKeyIds);
            ctx.pipeline().replace(this, "sslHandler", sslHandler).fireChannelActive();
        }
    }
}
