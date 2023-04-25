/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalServerChannel;
import java.net.InetSocketAddress;
import org.opendaylight.netconf.api.NetconfServerDispatcher;
import org.opendaylight.netconf.nettyutil.AbstractNetconfDispatcher;

public class NetconfServerDispatcherImpl extends AbstractNetconfDispatcher<NetconfServerSession,
        NetconfServerSessionListener> implements NetconfServerDispatcher {

    private final ServerChannelInitializer initializer;

    public NetconfServerDispatcherImpl(ServerChannelInitializer serverChannelInitializer, EventLoopGroup bossGroup,
                                       EventLoopGroup workerGroup) {
        super(bossGroup, workerGroup);
        this.initializer = serverChannelInitializer;
    }

    @Override
    public ChannelFuture createServer(InetSocketAddress address) {
        return super.createServer(address, initializer::initialize);
    }

    @Override
    public ChannelFuture createLocalServer(LocalAddress address) {
        return super.createServer(address, LocalServerChannel.class, initializer::initialize);
    }
}
