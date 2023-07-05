/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import java.util.concurrent.ThreadFactory;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.common.rev230417.tcp.common.grouping.Keepalives;

/**
 * Wrapper around a particular Netty transport implementation.
 */
@NonNullByDefault
abstract sealed class AbstractNettyImpl permits EpollNettyImpl, NioNettyImpl {

    abstract Class<? extends SocketChannel> channelClass();

    abstract Class<? extends ServerSocketChannel> serverChannelClass();

    abstract EventLoopGroup newEventLoopGroup(int numThreads, ThreadFactory threadFactory);

    abstract boolean supportsKeepalives();

    abstract void configureKeepalives(Bootstrap bootstrap, Keepalives keepalives);

    abstract void configureKeepalives(ServerBootstrap bootstrap, Keepalives keepalives);

    @Override
    public abstract String toString();
}