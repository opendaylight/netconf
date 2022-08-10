/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

public abstract sealed class TCPTransportStack extends AbstractTransportStack {
    private static final class Initiate extends TCPTransportStack {
        private final Future<?> shutdownFuture;

        Initiate(final Channel channel) {
            shutdownFuture = channel.newSucceededFuture();
        }

        @Override
        protected Future<?> startShutdown() {
            return shutdownFuture;
        }
    }

    private static final class Listen extends TCPTransportStack {
        private final Channel listenChannel;

        Listen(final Channel listenChannel) {
            this.listenChannel = requireNonNull(listenChannel);
        }

        @Override
        protected Future<?> startShutdown() {
            return listenChannel.close();
        }
    }

    public static @NonNull CompletionStage<TransportStack> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams)
                throws UnsupportedConfigurationException {
        NettyTransportSupport.configureKeepalives(bootstrap, connectParams.getKeepalives());

        final var ret = new CompletableFuture<TransportStack>();
        bootstrap
            .connect(
                socketAddressOf(connectParams.requireRemoteAddress(), connectParams.requireRemotePort()),
                socketAddressOf(connectParams.getLocalAddress(), connectParams.getLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // Order of operations is imporant here: the stack should be visible before the underlying channel.
                    final var channel = future.channel();
                    final var stack = new Initiate(channel);
                    ret.complete(stack);
                    if (stack.notShutdown()) {
                        listener.onTransportChannelEstablished(new TransportChannel(channel));
                    }
                } else {
                    ret.completeExceptionally(future.cause());
                }
            });
        return ret;
    }

    public static @NonNull CompletionStage<TransportStack> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams)
                throws UnsupportedConfigurationException {
        NettyTransportSupport.configureKeepalives(bootstrap, listenParams.getKeepalives());

        final var ret = new CompletableFuture<TransportStack>();
        bootstrap
            .bind(socketAddressOf(listenParams.requireLocalAddress(), listenParams.requireLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    ret.complete(new Listen(future.channel()));
                } else {
                    ret.completeExceptionally(future.cause());
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
