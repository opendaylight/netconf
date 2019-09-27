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
import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.Factory;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.session.ConnectionService;

/**
 * A {@link ClientSessionImpl} which additionally allows creation of NETCONF subsystem channel, which is routed to
 * a particular {@link ChannelHandlerContext}.
 */
@Beta
public class NetconfClientSessionImpl extends ClientSessionImpl implements NetconfClientSession {
    public static final Factory<SshClient> DEFAULT_NETCONF_SSH_CLIENT_FACTORY = SshClient::new;

    public NetconfClientSessionImpl(final ClientFactoryManager client, final IoSession ioSession) throws Exception {
        super(client, ioSession);
    }

    @Override
    public NetconfChannelSubsystem createNetconfSubsystemChannel(final ChannelHandlerContext ctx) throws IOException {
        final NetconfChannelSubsystem channel = new NetconfChannelSubsystem(ctx);
        final ConnectionService service = getConnectionService();
        final int id = service.registerChannel(channel);
        if (log.isDebugEnabled()) {
            log.debug("createSubsystemChannel({})[{}] created id={}", this, channel.getSubsystem(), id);
        }
        return channel;
    }
}
