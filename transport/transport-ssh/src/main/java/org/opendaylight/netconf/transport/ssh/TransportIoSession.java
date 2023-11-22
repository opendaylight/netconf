/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.SocketAddress;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoSession;

final class TransportIoSession extends NettyIoSession {
    private final ChannelInboundHandlerAdapter adapter = new Adapter() {
        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            // event expected by subsequent handlers
            ctx.fireChannelInactive();
        }
    };

    TransportIoSession(final TransportIoService service, final IoHandler handler,
            final SocketAddress acceptanceAddress) {
        super(service, handler, acceptanceAddress);
    }

    ChannelHandler getHandler() {
        return adapter;
    }
}
