/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalServerChannel;
import java.net.InetSocketAddress;
import org.opendaylight.netconf.nettyutil.AbstractNetconfDispatcher;
import org.opendaylight.netconf.server.api.NetconfServerDispatcher;

public class NetconfServerDispatcherImpl extends AbstractNetconfDispatcher<NetconfServerSession,
        NetconfServerSessionListener> implements NetconfServerDispatcher {
    private final ServerChannelInitializer initializer;

    public NetconfServerDispatcherImpl(final ServerChannelInitializer serverChannelInitializer,
            final EventLoopGroup bossGroup, final EventLoopGroup workerGroup) {
        super(bossGroup, workerGroup);
        initializer = serverChannelInitializer;
    }

    @Override
    public ChannelFuture createServer(final InetSocketAddress address) {
        return super.createServer(address, initializer::initialize);
    }

    @Override
    public ChannelFuture createLocalServer(final LocalAddress address) {
        return super.createServer(address, LocalServerChannel.class, initializer::initialize);
    }
}
