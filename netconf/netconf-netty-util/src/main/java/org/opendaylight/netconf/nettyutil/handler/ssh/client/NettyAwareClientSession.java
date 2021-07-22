/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import com.google.common.annotations.Beta;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;

/**
 * A {@link ClientSession} which additionally allows subsystem channels which are forwarded to a particular Netty
 * channel context.
 */
@Beta
public interface NettyAwareClientSession extends ClientSession {
    /**
     * Allocate a channel to the specified subsystem. Incoming data on the channel will be routed to the
     * specified {@link ChannelHandlerContext}.
     *
     * @param subsystem The subsystem name
     * @param ctx Context to which to route data to
     * @return The created {@link NettyAwareChannelSubsystem}
     * @throws IOException If failed to create the requested channel
     */
    NettyAwareChannelSubsystem createSubsystemChannel(String subsystem, ChannelHandlerContext ctx) throws IOException;

    /**
     * Allocate a channel to the specified subsystem. Incoming data on the channel will be routed to the
     * specified {@link ChannelPipeline}.
     *
     * @param subsystem The subsystem name
     * @param pipeline ChannelPipeline to which to route data to
     * @return The created {@link NettyPipelineAwareChannelSubsystem}
     * @throws IOException If failed to create the requested channel
     */
    NettyPipelineAwareChannelSubsystem createSubsystemChannel(String subsystem, ChannelPipeline pipeline)
            throws IOException;
}
