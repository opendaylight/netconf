/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A {@link TCPTransportStack} acting as a TCP client.
 */
public final class TCPClient extends TCPTransportStack {
    @Sharable
    private static final class ConnectChannelInitializer extends ChannelInitializer<Channel> {
        static final ConnectChannelInitializer INSTANCE = new ConnectChannelInitializer();

        @Override
        protected void initChannel(final Channel ch) {
            // No-op
        }
    }

    private static final @NonNull ListenableFuture<Empty> SHUTDOWN_FUTURE = Futures.immediateFuture(Empty.value());

    private TCPClient(final TransportChannelListener listener) {
        super(listener);
    }

    /**
     * Attempt to establish a {@link TCPClient} by connecting to a remote address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param bootstrap Client {@link Bootstrap} to use for the underlying Netty channel
     * @param connectParams Connection parameters
     * @return A future
     * @throws UnsupportedConfigurationException when {@code connectParams} contains an unsupported options
     * @throws NullPointerException if any argument is {@code null}
     */
    public static @NonNull ListenableFuture<TCPClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams)
                throws UnsupportedConfigurationException {
        NettyTransportSupport.configureKeepalives(bootstrap, connectParams.getKeepalives());

        final var ret = SettableFuture.<TCPClient>create();
        bootstrap
            .handler(ConnectChannelInitializer.INSTANCE)
            .connect(
                socketAddressOf(connectParams.requireRemoteAddress(), connectParams.requireRemotePort()),
                socketAddressOf(connectParams.getLocalAddress(), connectParams.getLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // Order of operations is important here: the stack should be visible before the underlying channel
                    final var stack = new TCPClient(listener);
                    ret.set(stack);
                    stack.addTransportChannel(new TCPTransportChannel(future.channel()));
                } else {
                    ret.setException(future.cause());
                }
            });
        return ret;
    }

    @Override
    protected ListenableFuture<Empty> startShutdown() {
        return SHUTDOWN_FUTURE;
    }
}