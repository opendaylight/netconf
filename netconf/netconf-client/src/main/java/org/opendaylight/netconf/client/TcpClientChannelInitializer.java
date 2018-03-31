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
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.net.SocketAddress;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;

class TcpClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> {

    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    TcpClientChannelInitializer(final NetconfClientSessionNegotiatorFactory negotiatorFactory,
                                final NetconfClientSessionListener sessionListener) {
        this.negotiatorFactory = negotiatorFactory;
        this.sessionListener = sessionListener;
    }

    @Override
    public void initialize(final Channel ch, final Promise<NetconfClientSession> promise) {
        final Future<NetconfClientSession> negotiationFuture = promise;

        //We have to add this channel outbound handler to channel pipeline, in order
        //to get notifications from netconf negotiatior. Set connection promise to
        //success only after successful negotiation.
        ch.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
            ChannelPromise connectPromise;
            GenericFutureListener<Future<NetconfClientSession>> negotiationFutureListener;

            @Override
            public void connect(final ChannelHandlerContext ctx, final SocketAddress remoteAddress,
                                final SocketAddress localAddress,
                                final ChannelPromise channelPromise) throws Exception {
                connectPromise = channelPromise;
                ChannelPromise tcpConnectFuture = new DefaultChannelPromise(ch);

                negotiationFutureListener = future -> {
                    if (future.isSuccess()) {
                        channelPromise.setSuccess();
                    }
                };

                tcpConnectFuture.addListener(future -> {
                    if (future.isSuccess()) {
                        //complete connection promise with netconf negotiation future
                        negotiationFuture.addListener(negotiationFutureListener);
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

                //If connection promise is not already set, it means negotiation failed
                //we must set connection promise to failure
                if (!connectPromise.isDone()) {
                    connectPromise.setFailure(new IllegalStateException("Negotiation failed"));
                }

                //Remove listener from negotiation future, we don't want notifications
                //from negotiation anymore
                negotiationFuture.removeListener(negotiationFutureListener);

                super.disconnect(ctx, promise);
                promise.setSuccess();
            }
        });

        super.initialize(ch, promise);
    }

    @Override
    protected void initializeSessionNegotiator(final Channel ch, final Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, AbstractChannelInitializer.NETCONF_SESSION_NEGOTIATOR,
                negotiatorFactory.getSessionNegotiator(() -> sessionListener, ch, promise));
    }
}
