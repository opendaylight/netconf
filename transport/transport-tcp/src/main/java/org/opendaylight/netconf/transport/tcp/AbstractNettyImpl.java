/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import java.util.concurrent.ThreadFactory;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Wrapper around a particular Netty transport implementation.
 */
@NonNullByDefault
abstract sealed class AbstractNettyImpl permits EpollNettyImpl, NioNettyImpl {
    /**
     * Channel options for TCP keepalives.
     *
     * @param tcpKeepCnt the option corresponding to {@code TCP_KEEPCNT}
     * @param tcpKeepIdle the option corresponding to {@code TCP_KEEPIDLE}
     * @param tcpKeepIntvl the option corresponding to {@code TCP_KEEPINTVL}
     */
    record TcpKeepaliveOptions(
            ChannelOption<Integer> tcpKeepCnt,
            ChannelOption<Integer> tcpKeepIdle,
            ChannelOption<Integer> tcpKeepIntvl) {
        TcpKeepaliveOptions {
            requireNonNull(tcpKeepCnt);
            requireNonNull(tcpKeepIdle);
            requireNonNull(tcpKeepIntvl);
        }
    }

    abstract Class<? extends DatagramChannel> datagramChannelClass();

    abstract Class<? extends SocketChannel> channelClass();

    abstract Class<? extends ServerSocketChannel> serverChannelClass();

    abstract EventLoopGroup newEventLoopGroup(int numThreads, ThreadFactory threadFactory);

    abstract @Nullable TcpKeepaliveOptions keepaliveOptions();

    @Override
    public abstract String toString();
}