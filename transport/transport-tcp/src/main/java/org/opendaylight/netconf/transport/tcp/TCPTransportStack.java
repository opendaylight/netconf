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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import java.net.InetSocketAddress;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A {@link TransportStack} based on {@code TCP} connections. Instantiated via
 * {@link #connect(TransportChannelListener, Bootstrap, TcpClientGrouping)} or
 * {@link #listen(TransportChannelListener, ServerBootstrap, TcpServerGrouping)}. Use {@link NettyTransportSupport} to
 * instantiate {@link Bootstrap} and {@link ServerBootstrap} instances and pre-configure if need be.
 */
public abstract sealed class TCPTransportStack extends AbstractTransportStack {
    private static final class Connect extends TCPTransportStack {
        private static final @NonNull ListenableFuture<Empty> SHUTDOWN_FUTURE = Futures.immediateFuture(Empty.value());

        @Override
        protected ListenableFuture<Empty> startShutdown() {
            return SHUTDOWN_FUTURE;
        }
    }

    private static final class Listen extends TCPTransportStack {
        private final Channel listenChannel;

        Listen(final Channel listenChannel) {
            this.listenChannel = requireNonNull(listenChannel);
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

    @Sharable
    private static final class ListenChannelInitializer extends ChannelInitializer<Channel> {
        private final TransportChannelListener listener;

        private volatile Listen stack;

        ListenChannelInitializer(final TransportChannelListener listener) {
            this.listener = requireNonNull(listener);
        }

        @Override
        protected void initChannel(final Channel ch) {
            final var local = verifyNotNull(stack, "Stack not initialized while handling channel %s", ch);
            if (local.notShutdown()) {
                listener.onTransportChannelEstablished(new TransportChannel(ch));
            } else {
                ch.close();
            }
        }

        void setStack(final Listen stack) {
            this.stack = requireNonNull(stack);
        }
    }

    public static @NonNull ListenableFuture<TCPTransportStack> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams)
                throws UnsupportedConfigurationException {
        NettyTransportSupport.configureKeepalives(bootstrap, connectParams.getKeepalives());

        final var ret = SettableFuture.<TCPTransportStack>create();
        bootstrap
            .connect(
                socketAddressOf(connectParams.requireRemoteAddress(), connectParams.requireRemotePort()),
                socketAddressOf(connectParams.getLocalAddress(), connectParams.getLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // Order of operations is important here: the stack should be visible before the underlying channel
                    final var stack = new Connect();
                    ret.set(stack);
                    if (stack.notShutdown()) {
                        listener.onTransportChannelEstablished(new TransportChannel(future.channel()));
                    }
                } else {
                    ret.setException(future.cause());
                }
            });
        return ret;
    }

    public static @NonNull ListenableFuture<TCPTransportStack> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams)
                throws UnsupportedConfigurationException {
        NettyTransportSupport.configureKeepalives(bootstrap, listenParams.getKeepalives());

        final var ret = SettableFuture.<TCPTransportStack>create();
        final var initializer = new ListenChannelInitializer(listener);
        bootstrap
            .childHandler(initializer)
            .bind(socketAddressOf(listenParams.requireLocalAddress(), listenParams.requireLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    final var stack = new Listen(future.channel());
                    initializer.setStack(stack);
                    ret.set(stack);
                } else {
                    ret.setException(future.cause());
                }
            });
        return ret;
    }

    private static InetSocketAddress socketAddressOf(final Host host, final PortNumber port) {
        final var addr = host.getIpAddress();
        return addr != null ? socketAddressOf(addr, port)
            : InetSocketAddress.createUnresolved(host.getDomainName().getValue(), port.getValue().toJava());
    }

    private static InetSocketAddress socketAddressOf(final IpAddress addr, final PortNumber port) {
        final int portNum = port == null ? 0 : port.getValue().toJava();
        if (addr == null) {
            return port == null ? null : new InetSocketAddress(portNum);
        }
        return new InetSocketAddress(IetfInetUtil.INSTANCE.inetAddressFor(addr), portNum);
    }
}
