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
import io.netty.channel.ChannelFutureListener;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A {@link TransportStack} acting as a TCP client.
 */
public final class TCPClient extends TCPTransportStack {
    private static final @NonNull ListenableFuture<Empty> SHUTDOWN_FUTURE = Futures.immediateFuture(Empty.value());

    private TCPClient() {
        // Hidden on purpose
    }

    public static @NonNull ListenableFuture<TCPClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams)
                throws UnsupportedConfigurationException {
        NettyTransportSupport.configureKeepalives(bootstrap, connectParams.getKeepalives());

        final var ret = SettableFuture.<TCPClient>create();
        bootstrap
            .connect(
                socketAddressOf(connectParams.requireRemoteAddress(), connectParams.requireRemotePort()),
                socketAddressOf(connectParams.getLocalAddress(), connectParams.getLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // Order of operations is important here: the stack should be visible before the underlying channel
                    final var stack = new TCPClient();
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

    @Override
    protected ListenableFuture<Empty> startShutdown() {
        return SHUTDOWN_FUTURE;
    }
}