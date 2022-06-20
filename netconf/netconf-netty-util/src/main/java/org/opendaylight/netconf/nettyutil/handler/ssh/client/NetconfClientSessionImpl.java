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
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSessionImpl;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.session.ConnectionService;

/**
 * A {@link ClientSessionImpl} which additionally allows creation of NETCONF subsystem channel, which is routed to
 * a particular {@link ChannelHandlerContext}.
 */
@Beta
public class NetconfClientSessionImpl extends ClientSessionImpl implements NettyAwareClientSession {
    public NetconfClientSessionImpl(final ClientFactoryManager client, final IoSession ioSession) throws Exception {
        super(client, ioSession);
    }

    @Override
    public NettyAwareChannelSubsystem createSubsystemChannel(final String subsystem, final ChannelHandlerContext ctx)
            throws IOException {
        final NettyAwareChannelSubsystem channel = new NettyAwareChannelSubsystem(subsystem, ctx);
        final ConnectionService service = getConnectionService();
        final int id = service.registerChannel(channel);
        if (log.isDebugEnabled()) {
            log.debug("createSubsystemChannel({})[{}] created id={}", this, channel.getSubsystem(), id);
        }
        return channel;
    }
}
