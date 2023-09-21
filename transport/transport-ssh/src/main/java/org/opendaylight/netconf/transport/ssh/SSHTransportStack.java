/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.Channel;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.ssh.NettyIoChannelHandler.HandlerAndId;

/**
 * An SSH {@link TransportStack}. Instances of this class are built indirectly.
 */
public abstract sealed class SSHTransportStack extends AbstractOverlayTransportStack<SSHTransportChannel>
        permits SSHClient, SSHServer {
    // FIXME: hide these fields
    final Map<Long, UserAuthSessionListener.AuthHandler> sessionAuthHandlers = new ConcurrentHashMap<>();
    final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    SSHTransportStack(final TransportChannelListener listener) {
        super(listener);
    }

    @Override
    protected final void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        final var channel = underlayChannel.channel();
        final var handlerAndId = createHandler(channel);

        channel.pipeline().addLast(handlerAndId.handler());
        // authentication triggering and handlers processing is performed by UserAuthSessionListener
        sessionAuthHandlers.put(handlerAndId.id(), new UserAuthSessionListener.AuthHandler(
            // auth success
            () -> addTransportChannel(new SSHTransportChannel(underlayChannel)),
            // auth failure
            () -> channel.close())
        );
    }

    abstract HandlerAndId createHandler(Channel channel);

    @VisibleForTesting
    final Collection<Session> getSessions() {
        return sessions.values();
    }
}
