/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.Objects.requireNonNull;

import io.netty.handler.ssl.SslContext;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

/**
 * Base class for TLS TransportStacks.
 */
public abstract sealed class TLSTransportStack extends AbstractOverlayTransportStack<TLSTransportChannel>
        permits TLSClient, TLSServer {
    private final SslHandlerFactory factory;

    TLSTransportStack(final TransportChannelListener listener, final SslContext sslContext) {
        this(listener, new FixedSslHandlerFactory(sslContext));
    }

    TLSTransportStack(final TransportChannelListener listener, final SslHandlerFactory factory) {
        super(listener);
        this.factory = requireNonNull(factory);
    }

    @Override
    protected final void onUnderlayChannelEstablished(final TLSTransportChannel underlayChannel) {
        final var channel = underlayChannel.channel();
        final var sslHandler = factory.createSslHandler(channel);

        channel.pipeline().addLast(sslHandler);
        sslHandler.handshakeFuture().addListener(future -> {
            final var cause = future.cause();
            if (cause != null) {
                notifyTransportChannelFailed(cause);
                channel.close();
            } else {
                addTransportChannel(new TLSTransportChannel(underlayChannel));
            }
        });
    }
}
