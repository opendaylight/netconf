/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

/**
 * Wrapper around a particular Netty transport implementation.
 */
@NonNullByDefault
abstract sealed class NettyImpl permits EpollNettyImpl, NioNettyImpl {

    abstract Class<? extends DatagramChannel> datagramChannelClass();

    abstract Class<? extends SocketChannel> channelClass();

    abstract Class<? extends ServerSocketChannel> serverChannelClass();

    abstract IoHandlerFactory ioHandlerFactory();

    abstract @Nullable NettyTcpKeepaliveOptions keepaliveOptions();

    abstract void setTcpMd5(AbstractBootstrap<?, ?> bootstrap, TcpMd5Secrets secrets)
        throws UnsupportedConfigurationException;

    abstract boolean setTcpMd5(Channel channel, TcpMd5Secrets secrets) throws UnsupportedConfigurationException;

    @Override
    public abstract String toString();
}