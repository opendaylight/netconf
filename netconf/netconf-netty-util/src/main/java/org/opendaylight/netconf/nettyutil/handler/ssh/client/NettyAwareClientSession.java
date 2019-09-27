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
import java.io.IOException;
import org.apache.sshd.client.session.ClientSession;

/**
 * A {@link ClientSession} which additionally allows subsystem channels which are forwarded to a particular Netty
 * channel context.
 */
@Beta
public interface NettyAwareClientSession extends ClientSession {
    /**
     * Allocate a channel to the specified subsystem subsystem. Incoming data on the channel will be routed to the
     * specified ChannelHandlerContext.
     *
     * @param
     * @param ctx Context to which to route data to
     * @return Subsystem channel
     * @throws IOException If failed to create the requested channel
     */
    NettyAwareChannelSubsystem createSubsystemChannel(String subsystem, ChannelHandlerContext ctx) throws IOException;
}
