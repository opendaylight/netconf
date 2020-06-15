/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.GenericFutureListener;
import org.opendaylight.netconf.client.SslHandlerFactory;

@SuppressWarnings("rawtypes")
final class TlsAuthChannelInitializer extends ChannelInitializer {

    private static final String SSL_HANDLER_CHANNEL_NAME = "sslHandler";

    private final SslHandlerFactory sslHandlerFactory;
    private final GenericFutureListener listener;

    TlsAuthChannelInitializer(final SslHandlerFactory sslHandlerFactory, final GenericFutureListener listener) {
        this.sslHandlerFactory = sslHandlerFactory;
        this.listener = listener;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void initChannel(final Channel ch) {
        final SslHandler sslHandler = sslHandlerFactory.createSslHandler();
        sslHandler.handshakeFuture().addListener(listener);
        ch.pipeline().addFirst(SSL_HANDLER_CHANNEL_NAME, sslHandler).fireChannelActive();
    }
}