/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;

/**
 * An {@link AbstractNettyChannelSubsystem} for subsystem which routes incoming data to a particular
 * {@link ChannelHandlerContext}.
 */
// Non-final for testing
non-sealed class NettyAwareChannelSubsystem extends AbstractNettyChannelSubsystem {
    private final ChannelHandlerContext ctx;

    NettyAwareChannelSubsystem(final String subsystem, final ChannelHandlerContext ctx) {
        super(subsystem);
        this.ctx = requireNonNull(ctx);
    }

    @Override
    final ChannelHandlerContext context() {
        return ctx;
    }
}
