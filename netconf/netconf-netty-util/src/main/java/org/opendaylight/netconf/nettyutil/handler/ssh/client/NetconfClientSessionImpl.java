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
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSessionImpl;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;

/**
 * A {@link ClientSessionImpl} which additionally allows creation of NETCONF subsystem channel, which is routed to
 * a particular {@link ChannelHandlerContext}.
 */
@Beta
public final class NetconfClientSessionImpl extends ClientSessionImpl implements NettyAwareClientSession {
    public NetconfClientSessionImpl(final ClientFactoryManager client, final IoSession ioSession) throws Exception {
        super(client, ioSession);
    }

    @Override
    public ChannelSubsystem createSubsystemChannel(final String subsystem, final ChannelHandlerContext ctx)
            throws IOException {
        return registerSubsystem(new NettyAwareChannelSubsystem(subsystem, ctx));
    }

    @Override
    public ChannelSubsystem createSubsystemChannel(final String subsystem,
            final ChannelPipeline pipeline) throws IOException {
        return registerSubsystem(new NettyPipelineAwareChannelSubsystem(subsystem, pipeline));
    }

    private ChannelSubsystem registerSubsystem(final ChannelSubsystem subsystem) throws IOException {
        final var service = getConnectionService();
        final var id = service.registerChannel(subsystem);
        if (log.isDebugEnabled()) {
            log.debug("createSubsystemChannel({})[{}] created id={}", this, subsystem.getSubsystem(), id);
        }
        return subsystem;
    }
}
