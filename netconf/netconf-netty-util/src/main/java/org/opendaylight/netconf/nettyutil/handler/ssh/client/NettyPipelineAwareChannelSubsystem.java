/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

/**
 * A {@link AbstractNettyChannelSubsystem} for subsystem which routes incoming data to a particular
 * {@link ChannelPipeline}.
 */
// Non-final for testing
non-sealed class NettyPipelineAwareChannelSubsystem extends AbstractNettyChannelSubsystem {
    private final ChannelPipeline pipeline;

    NettyPipelineAwareChannelSubsystem(final String subsystem, final ChannelPipeline pipeline) {
        super(subsystem);
        this.pipeline = requireNonNull(pipeline);
    }

    @Override
    final ChannelHandlerContext context() {
        return pipeline.firstContext();
    }
}
