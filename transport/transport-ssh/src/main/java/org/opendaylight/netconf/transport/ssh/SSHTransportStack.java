/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;

/**
 * An SSH {@link TransportStack}. Instances of this class are built indirectly. The setup of the Netty channel is quite
 * weird. We start off with whatever the underlay sets up.
 *
 * <p>
 * We then add {@link TransportIoSession#getHandler()}, which routes data between the socket and
 * {@link TransportSshClient} (or {@link TransportSshServer}) -- forming the "bottom half" of the channel.
 *
 * <p>
 * The "upper half" of the channel is formed once the corresponding SSH subsystem is established, via
 * {@link TransportClientSubsystem}, which installs a {@link OutboundChannelHandler}. These two work together:
 * <ul>
 *   <li>TransportClientSubsystem pumps bytes inbound from the subsystem towards the tail of the channel pipeline</li>
 *   <li>OutboundChannelHandler pumps bytes outbound from the tail of channel pipeline into the subsystem</li>
 * </ul>
 */
public abstract sealed class SSHTransportStack extends AbstractOverlayTransportStack<SSHTransportChannel>
        implements SessionListener permits SSHClient, SSHServer {
    private final Map<Long, TransportChannel> unauthUnderlays = new ConcurrentHashMap<>();
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();
    private final TransportIoService ioService;

    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR", justification = """
        SessionListener has default implementations which we do not care about. We have all subclasses in this package
        and neither of them has additional state""")
    SSHTransportStack(final TransportChannelListener listener, final FactoryManager factoryManager,
            final IoHandler handler) {
        super(listener);
        ioService = new TransportIoService(factoryManager, handler);
        factoryManager.addSessionListener(this);
    }

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        // Acquire underlying channel, create a TransportIoSession and attach its handler to this channel -- which takes
        // care of routing bytes between the underlay channel and SSHD's network-facing side.
        final var channel = underlayChannel.channel();
        final var ioSession = ioService.createSession(channel.localAddress());
        channel.pipeline().addLast(ioSession.getHandler());

        // we now have an attached underlay, but it needs further processing before we expose it to the end user
        unauthUnderlays.put(ioSession.getId(), underlayChannel);
    }

    /*
     * SessionListener integration. Responsible for session authentication for both client and server.
     *
     * <P>Triggers authentication flow when after server key is accepted by client,
     * invokes associated handler on authentication success/failure.
     */
    @Override
    public final void sessionCreated(final Session session) {
        sessions.put(session.getIoSession().getId(), session);
    }

    @Override
    public final void sessionException(final Session session, final Throwable throwable) {
        deleteSession(session);
    }

    @Override
    public final void sessionDisconnect(final Session session, final int reason, final String msg,
            final String language, final boolean initiator) {
        deleteSession(session);
    }

    @Override
    public final void sessionClosed(final Session session) {
        deleteSession(session);
    }

    @Override
    public final void sessionEvent(final Session session, final Event event) {
        switch (event) {
            case Authenticated:
                // auth success
                try {
                    onAuthenticated(session);
                } catch (IOException e) {
                    sessionException(session, e);
                }
                break;
            case KeyEstablished:
                try {
                    onKeyEstablished(session);
                } catch (IOException e) {
                    sessionException(session, e);
                }
                break;
            default:
                // No-op
        }
    }

    abstract void onKeyEstablished(Session session) throws IOException;

    abstract void onAuthenticated(Session session) throws IOException;

    final @Nullable TransportChannel underlayOf(final Session session) {
        return unauthUnderlays.get(idOf(session));
    }

    final void deleteSession(final Session session) {
        final var id = idOf(session);
        sessions.remove(id);
        // auth failure, close underlay if any
        completeUnderlay(id, underlay -> underlay.channel().close());
    }

    final void completeUnderlay(final Session session, final Consumer<TransportChannel> action) {
        completeUnderlay(idOf(session), action);
    }

    private void completeUnderlay(final Long sessionId, final Consumer<TransportChannel> action) {
        final var removed = unauthUnderlays.remove(sessionId);
        if (removed != null) {
            action.accept(removed);
        }
    }

    private static Long idOf(final Session session) {
        return session.getIoSession().getId();
    }

    @VisibleForTesting
    Collection<Session> getSessions() {
        return sessions.values();
    }
}
