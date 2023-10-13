/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for {@link NettyAwareChannelSubsystem} and {@link NettyPipelineAwareChannelSubsystem}.
 */
abstract sealed class AbstractNettyChannelSubsystem extends ChannelSubsystem
        permits NettyAwareChannelSubsystem, NettyPipelineAwareChannelSubsystem {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNettyChannelSubsystem.class);

    AbstractNettyChannelSubsystem(final String subsystem) {
        super(subsystem);
    }

    @Override
    public final void close() {
        close(false);
    }

    @Override
    protected final void doWriteExtendedData(final byte[] data, final int off, final long len) throws IOException {
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
    protected final void doWriteData(final byte[] data, final int off, final long len) throws IOException {
        // If we're already closing, ignore incoming data
        if (isClosing()) {
            return;
        }

        // TODO: consider using context's allocator for heap buffer here
        final int reqLen = (int) len;
        context().fireChannelRead(Unpooled.copiedBuffer(data, off, reqLen));
        if (reqLen > 0) {
            getLocalWindow().release(reqLen);
        }
    }

    abstract ChannelHandlerContext context();
}
