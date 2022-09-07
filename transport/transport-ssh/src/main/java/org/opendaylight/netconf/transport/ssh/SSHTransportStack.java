/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoService;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;

/**
 * An SSH {@link TransportStack}. Instances of this class are built indirectly.
 */
public abstract sealed class SSHTransportStack extends AbstractOverlayTransportStack<SSHTransportChannel>
        permits SSHClient, SSHServer {

    protected final Map<Long, UserAuthSessionListener.AuthHandler> sessionAuthHandlers = new ConcurrentHashMap<>();
    protected final Map<Long, Session> sessions = new ConcurrentHashMap<>();
    protected NettyIoService ioService;

    SSHTransportStack(final TransportChannelListener listener) {
        super(listener);
    }

    @Override
    protected void onUnderlayChannelEstablished(@NonNull TransportChannel underlayChannel) {
        var channel = underlayChannel.channel();
        final SshIoSession ioSession = new SshIoSession(ioService, getSessionFactory(), channel.localAddress());
        channel.pipeline().addLast(ioSession.getHandler());
        // authentication triggering and handlers processing is performed by UserAuthSessionListener
        sessionAuthHandlers.put(ioSession.getId(), new UserAuthSessionListener.AuthHandler(
                () -> addTransportChannel(new SSHTransportChannel(underlayChannel)), // auth success
                () -> channel.close()) // auth failure
        );
    }

    abstract IoHandler getSessionFactory();

    public Collection<Session> getSessions() {
        return sessions.values();
    }

}
