/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.udp;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.api.ClientStackInitializer;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.udp.client.rev241004.UdpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.udp.server.rev241004.UdpServerGrouping;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 *
 */
public final class UDPTransportStack extends AbstractTransportStack<UDPTransportChannel> {
    private UDPTransportStack(final TransportChannelListener<UDPTransportChannel> listener) {
        super(listener);
    }

    public static @NonNull ListenableFuture<UDPTransportStack> connect(
            final TransportChannelListener<UDPTransportChannel> listener, final Bootstrap bootstrap,
            final UdpClientGrouping connectParams) throws UnsupportedConfigurationException {
        final var ret = SettableFuture.<UDPTransportStack>create();
        final var stack = new UDPTransportStack(listener);
        bootstrap
            .handler(new ClientStackInitializer<>(stack, UDPTransportChannel::new))
            .connect(
                socketAddressOf(connectParams.requireRemoteAddress(), connectParams.requireRemotePort()),
                socketAddressOf(connectParams.getLocalAddress(), connectParams.getLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                final var cause = future.cause();
                if (cause != null) {
                    ret.setException(cause);
                }
            });
        return ret;
    }

    public static @NonNull ListenableFuture<UDPTransportStack> listen(
            final TransportChannelListener<UDPTransportChannel> listener, final ServerBootstrap bootstrap,
            final UdpServerGrouping listenParams) throws UnsupportedConfigurationException {
        final var localBinds = listenParams.nonnullLocalBind().values();
        final var localBind = switch (localBinds.size()) {
            case 0 -> throw new UnsupportedConfigurationException("No bind addresses provided");
            case 1 -> localBinds.iterator().next();
            // FIXME: support this case. it is slightly problematic, as we need to provide all-or-nothing semantics
            //        without leaking channels
            default -> throw new UnsupportedConfigurationException("Multiple bind addresses provided");
        };

        final var ret = SettableFuture.<UDPTransportStack>create();
        final var stack = new UDPTransportStack(listener);

        bootstrap
            .handler(new ClientStackInitializer<>(stack, UDPTransportChannel::new))
            .bind(socketAddressOf(localBind.requireLocalAddress(), localBind.requireLocalPort()))
            .addListener((ChannelFutureListener) future -> {
                final var cause = future.cause();
                if (cause != null) {
                    ret.setException(cause);
                }
            });
        return ret;
    }

    @Override
    protected ListenableFuture<Empty> startShutdown() {
        return Empty.immediateFuture();
    }
}
