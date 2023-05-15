/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ChannelSubsystem} for subsystem which routes incoming data to a particular {@link ChannelHandlerContext}.
 */
@Beta
public class NettyAwareChannelSubsystem extends ChannelSubsystem {
    private static final Logger LOG = LoggerFactory.getLogger(NettyAwareChannelSubsystem.class);

    private final ChannelHandlerContext ctx;

    public NettyAwareChannelSubsystem(final String subsystem, final ChannelHandlerContext ctx) {
        super(subsystem);
        this.ctx = requireNonNull(ctx);
    }

    @Override
    protected void doWriteData(final byte[] data, final int off, final long len) throws IOException {
        // If we're already closing, ignore incoming data
        if (isClosing()) {
            return;
        }

        // TODO: consider using context's allocator for heap buffer here
        final int reqLen = (int) len;
        ctx.fireChannelRead(Unpooled.copiedBuffer(data, off, reqLen));
        if (reqLen > 0) {
            getLocalWindow().release(reqLen);
        }
    }

    @Override
    protected void doWriteExtendedData(final byte[] data, final int off, final long len) throws IOException {
        // If we're already closing, ignore incoming data
        if (isClosing()) {
            return;
        }

        LOG.debug("Discarding {} bytes of extended data", len);
        if (len > 0) {
            getLocalWindow().release(len);
        }
    }

    @Override
    public void close() {
        this.close(false);
    }
}
