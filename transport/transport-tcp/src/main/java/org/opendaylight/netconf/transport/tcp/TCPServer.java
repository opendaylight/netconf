/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A {@link TCPTransportStack} acting as a TCP server.
 */
public final class TCPServer extends TCPTransportStack {
    @Sharable
    private static final class ListenChannelInitializer extends ChannelInitializer<Channel> {
        private final TCPServer stack;

        ListenChannelInitializer(final TCPServer stack) {
            this.stack = requireNonNull(stack);
        }

        @Override
        protected void initChannel(final Channel ch) {
            verifyNotNull(stack, "Stack not initialized while handling channel %s", ch)
                .addTransportChannel(new TCPTransportChannel(ch));
        }
    }

    private volatile Channel listenChannel;

    private TCPServer(final TransportChannelListener listener) {
        super(listener);
    }

    /**
     * Attempt to establish a {@link TCPClient} by connecting to a remote address.
     *
     * @param listener {@link TransportChannelListener} to notify when the session is established
     * @param bootstrap {@link ServerBootstrap} to use for the underlying Netty server channel
     * @param listenParams Listening parameters
     * @return A future
     * @throws UnsupportedConfigurationException when {@code listenParams} contains an unsupported options
     * @throws NullPointerException if any argument is {@code null}
     */
    public static @NonNull ListenableFuture<TCPServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams)
                throws UnsupportedConfigurationException {
        NettyTransportSupport.configureKeepalives(bootstrap, listenParams.getKeepalives());

        final var ret = SettableFuture.<TCPServer>create();
        final var stack = new TCPServer(listener);
        final var initializer = new ListenChannelInitializer(stack);
        bootstrap
            .childHandler(initializer)
            .bind(socketAddressOf(listenParams.requireLocalAddress(), listenParams.requireLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    stack.setListenChannel(future.channel());
                    ret.set(stack);
                } else {
                    ret.setException(future.cause());
                }
            });
        return ret;
    }

    @Override
    protected ListenableFuture<Empty> startShutdown() {
        return toListenableFuture(listenChannel().close());
    }

    @VisibleForTesting
    @NonNull Channel listenChannel() {
        return verifyNotNull(listenChannel, "Channel not initialized");
    }

    private void setListenChannel(final Channel listenChannel) {
        this.listenChannel = requireNonNull(listenChannel);
    }
}
