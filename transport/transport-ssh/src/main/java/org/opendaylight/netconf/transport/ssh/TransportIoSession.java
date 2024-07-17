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
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.util.Readable;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TransportIoSession extends NettyIoSession {
    private static final Logger LOG = LoggerFactory.getLogger(TransportIoSession.class);
    private final CountDownLatch initialized = new CountDownLatch(1);

    TransportIoSession(final TransportIoService service, final IoHandler handler,
            final SocketAddress acceptanceAddress) {
        super(service, handler, acceptanceAddress);
    }

    ChannelHandler getHandler() {
        return adapter;
    }

    @Override
    protected void channelActive(final ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        initialized.countDown();
    }

    @Override
    protected void channelRead(final ChannelHandlerContext ctx, final Readable msg) throws Exception {
        // channelActive() builds the SSH Session instance and attaches it to current IoSession instance;
        // current method expects the SSH Session being already attached and can be accessed for data input,
        // so we need assurance the channelActive() method execution is completed before executing current one
        if (!initialized.await(2, TimeUnit.SECONDS)) {
            LOG.warn("Read operation is invoked while initialization cannot be completed for more than 2 sec");
        }
        super.channelRead(ctx, msg);
    }

    @Override
    protected void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        // adapter is not propagating fireChannelInactive() down the pipeline, but instead loops here.
        // Once we have cleaned up, propagate fireChannelInactive() by ourselves.
        super.channelInactive(ctx);
        ctx.fireChannelInactive();
    }
}
