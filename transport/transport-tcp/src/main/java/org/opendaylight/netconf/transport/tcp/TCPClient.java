/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.ClientStackInitializer;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.spi.NettyTransportSupport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev241010.TcpClientGrouping;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A {@link TCPTransportStack} acting as a TCP client.
 */
public final class TCPClient extends TCPTransportStack {
    private TCPClient(final TransportChannelListener<? super TCPTransportChannel> listener) {
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
        final var keepalives = connectParams.getKeepalives();
        if (keepalives != null) {
            final var options = NettyTransportSupport.getTcpKeepaliveOptions();
            bootstrap
                .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
                .option(options.tcpKeepIdle(), keepalives.requireIdleTime().toJava())
                .option(options.tcpKeepCnt(), keepalives.requireMaxProbes().toJava())
                .option(options.tcpKeepIntvl(), keepalives.requireProbeInterval().toJava());
        }

        final var ret = SettableFuture.<TCPClient>create();
        final var stack = new TCPClient(listener);
        bootstrap
            .handler(new ClientStackInitializer<>(stack, TCPTransportChannel::new))
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

    @Override
    protected ListenableFuture<Empty> startShutdown() {
        return Empty.immediateFuture();
    }
}
