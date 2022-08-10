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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A {@link TransportStack} acting as a TCP server.
 */
public final class TCPServer extends TCPTransportStack {
    @Sharable
    private static final class ListenChannelInitializer extends ChannelInitializer<Channel> {
        private final TransportChannelListener listener;

        private volatile TCPServer stack;

        ListenChannelInitializer(final TransportChannelListener listener) {
            this.listener = requireNonNull(listener);
        }

        @Override
        protected void initChannel(final Channel ch) {
            final var local = verifyNotNull(stack, "Stack not initialized while handling channel %s", ch);
            if (local.notShutdown()) {
                listener.onTransportChannelEstablished(new TCPTransportChannel(ch));
            } else {
                ch.close();
            }
        }

        void setStack(final TCPServer stack) {
            this.stack = requireNonNull(stack);
        }
    }

    private final Channel listenChannel;

    private TCPServer(final TransportChannelListener listener, final Channel listenChannel) {
        super(listener);
        this.listenChannel = requireNonNull(listenChannel);
    }

    public static @NonNull ListenableFuture<TCPServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams)
                throws UnsupportedConfigurationException {
        NettyTransportSupport.configureKeepalives(bootstrap, listenParams.getKeepalives());

        final var ret = SettableFuture.<TCPServer>create();
        final var initializer = new ListenChannelInitializer(listener);
        bootstrap
            .childHandler(initializer)
            .bind(socketAddressOf(listenParams.requireLocalAddress(), listenParams.requireLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    final var stack = new TCPServer(listener, future.channel());
                    initializer.setStack(stack);
                    ret.set(stack);
                } else {
                    ret.setException(future.cause());
                }
            });
        return ret;
    }

    @Override
    protected ListenableFuture<Empty> startShutdown() {
        final var ret = SettableFuture.<Empty>create();
        listenChannel.close().addListener(future -> {
            if (future.isSuccess()) {
                ret.set(Empty.value());
            } else {
                ret.setException(future.cause());
            }
        });
        return ret;
    }
}