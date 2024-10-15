/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
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
public abstract sealed class NettyImpl permits EpollNettyImpl, NioNettyImpl {
    /**
     * Channel options for TCP keepalives.
     *
     * @param tcpKeepCnt the option corresponding to {@code TCP_KEEPCNT}
     * @param tcpKeepIdle the option corresponding to {@code TCP_KEEPIDLE}
     * @param tcpKeepIntvl the option corresponding to {@code TCP_KEEPINTVL}
     */
    public record TcpKeepaliveOptions(
            ChannelOption<Integer> tcpKeepCnt,
            ChannelOption<Integer> tcpKeepIdle,
            ChannelOption<Integer> tcpKeepIntvl) {
        public TcpKeepaliveOptions {
            requireNonNull(tcpKeepCnt);
            requireNonNull(tcpKeepIdle);
            requireNonNull(tcpKeepIntvl);
        }
    }

    private static final class Holder {
        static final NettyImpl INSTANCE = Epoll.isAvailable() ? new EpollNettyImpl() : NioNettyImpl.INSTANCE;

        private Holder() {
            // Hidden on purpose
        }
    }

    public static NettyImpl instance() {
        return Holder.INSTANCE;
    }

    public abstract Class<? extends DatagramChannel> datagramChannelClass();

    public abstract Class<? extends SocketChannel> channelClass();

    public abstract Class<? extends ServerSocketChannel> serverChannelClass();

    public abstract EventLoopGroup newEventLoopGroup(int numThreads, ThreadFactory threadFactory);

    public abstract @Nullable TcpKeepaliveOptions keepaliveOptions();

    @Override
    public abstract String toString();
}