/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import com.google.common.annotations.Beta;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.Factory;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.session.ConnectionService;

@Beta
public class NetconfSshClientSession extends ClientSessionImpl {
    public static final Factory<SshClient> DEFAULT_NETCONF_SSH_CLIENT_FACTORY = SshClient::new;

    public NetconfSshClientSession(final ClientFactoryManager client, final IoSession ioSession) throws Exception {
        super(client, ioSession);
    }

    public NetconfChannelSubsystem createNetconfSubsystemChannel(final ChannelHandlerContext ctx) throws IOException {
        NetconfChannelSubsystem channel = new NetconfChannelSubsystem(ctx);
        ConnectionService service = getConnectionService();
        int id = service.registerChannel(channel);
        if (log.isDebugEnabled()) {
            log.debug("createSubsystemChannel({})[{}] created id={}", this, channel.getSubsystem(), id);
        }
        return channel;
    }

}
