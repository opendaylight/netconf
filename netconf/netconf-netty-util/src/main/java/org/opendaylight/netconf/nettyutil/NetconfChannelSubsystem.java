/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import org.apache.sshd.client.channel.ChannelSubsystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Beta
public class NetconfChannelSubsystem extends ChannelSubsystem {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfChannelSubsystem.class);
    private static final String SUBSYSTEM = "netconf";

    private final ChannelHandlerContext ctx;

    public NetconfChannelSubsystem(final ChannelHandlerContext ctx) {
        super(SUBSYSTEM);
        this.ctx = requireNonNull(ctx);
    }

    @Override
    protected void doWriteData(final byte[] data, final int off, final long len) throws IOException {
        // If we're already closing, ignore incoming data
        if (isClosing()) {
            return;
        }

        ctx.fireChannelRead(Unpooled.copiedBuffer(data, off, (int) len));
    }

    @Override
    protected void doWriteExtendedData(final byte[] data, final int off, final long len) throws IOException {
        // If we're already closing, ignore incoming data
        if (isClosing()) {
            return;
        }

        LOG.debug("Discarding %s bytes of extended data", len);
    }
}
