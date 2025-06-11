/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import io.netty.channel.IoHandlerFactory;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
final class KQueueNettyImpl extends NettyImpl {
    private final IoHandlerFactory ioHandlerFactory = KQueueIoHandler.newFactory();

    @Override
    Class<KQueueDatagramChannel> datagramChannelClass() {
        return KQueueDatagramChannel.class;
    }

    @Override
    Class<KQueueSocketChannel> channelClass() {
        return KQueueSocketChannel.class;
    }

    @Override
    Class<KQueueServerSocketChannel> serverChannelClass() {
        return KQueueServerSocketChannel.class;
    }

    @Override
    IoHandlerFactory ioHandlerFactory() {
        return ioHandlerFactory;
    }

    @Override
    @Nullable NettyTcpKeepaliveOptions keepaliveOptions() {
        // No support just yet: https://github.com/netty/netty/issues/9780
        return null;
    }

    @Override
    public String toString() {
        return "kqueue(2)";
    }
}
