/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.net.SocketAddress;
import org.opendaylight.yangtools.concepts.AbstractRegistration;

/**
 * Client channel initializer for TCP transport.
 *
 * @deprecated due to design change. Common {@link ClientChannelInitializer} expected to be used while
 *      transport is served by dedicated components.
 */
@Deprecated
final class TcpClientChannelInitializer extends AbstractClientChannelInitializer {
    private static final class SessionCallback extends AbstractRegistration
            implements FutureCallback<NetconfClientSession> {
        private final ChannelPromise channelPromise;

        SessionCallback(final ChannelPromise channelPromise) {
            this.channelPromise = requireNonNull(channelPromise);
        }

        @Override
        public void onSuccess(final NetconfClientSession result) {
            if (notClosed()) {
                channelPromise.setSuccess();
                close();
            }
        }

        @Override
        public void onFailure(final Throwable t) {
            // FIXME: we ignore this failure, why exactly?
        }

        @Override
        protected void removeRegistration() {
            // No-op
        }
    }

    TcpClientChannelInitializer(final NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final NetconfClientSessionListener sessionListener) {
        super(negotiatorFactory, sessionListener);
    }

    @Override
    public void initialize(final Channel ch, final SettableFuture<NetconfClientSession> promise) {
        //We have to add this channel outbound handler to channel pipeline, in order
        //to get notifications from netconf negotiatior. Set connection promise to
        //success only after successful negotiation.
        ch.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
            ChannelPromise connectPromise;
            SessionCallback negotiationFutureListener;

            @Override
            public void connect(final ChannelHandlerContext ctx, final SocketAddress remoteAddress,
                    final SocketAddress localAddress, final ChannelPromise channelPromise) {
                connectPromise = channelPromise;
                ChannelPromise tcpConnectFuture = ch.newPromise();

                negotiationFutureListener = new SessionCallback(channelPromise);

                tcpConnectFuture.addListener(future -> {
                    if (future.isSuccess()) {
                        //complete connection promise with netconf negotiation future
                        Futures.addCallback(promise, negotiationFutureListener, MoreExecutors.directExecutor());
                    } else {
                        channelPromise.setFailure(future.cause());
                    }
                });
                ctx.connect(remoteAddress, localAddress, tcpConnectFuture);
            }

            @Override
            public void disconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
                if (connectPromise == null) {
                    return;
                }

                // If we have already succeeded and the session was dropped after, we need to fire inactive to notify
                // reconnect logic
                if (connectPromise.isSuccess()) {
                    ctx.fireChannelInactive();
                }

                // If connection promise is not already set, it means negotiation failed. We must set connection promise
                // to failure
                if (!connectPromise.isDone()) {
                    connectPromise.setFailure(new IllegalStateException("Negotiation failed"));
                }

                // Disable listener from negotiation future, we don't want notifications from negotiation anymore
                negotiationFutureListener.close();

                super.disconnect(ctx, promise);
                promise.setSuccess();
            }
        });

        super.initialize(ch, promise);
    }
}
