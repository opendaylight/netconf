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
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.SshConstants;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;
import org.opendaylight.netconf.transport.api.AbstractOverlayTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.tcp.TCPTransportChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(SSHTransportStack.class);

    // Underlay TransportChannels which do not have an open subsystem
    private final Map<Long, SSHTransportChannel> underlays = new ConcurrentHashMap<>();
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
    protected void onUnderlayChannelEstablished(final SSHTransportChannel underlayChannel) {
        LOG.debug("Underlay establishing, attaching SSH to {}", underlayChannel);
        // Acquire underlying channel, create a TransportIoSession and attach its handler to this channel -- which takes
        // care of routing bytes between the underlay channel and SSHD's network-facing side.
        final var channel = underlayChannel.channel();
        final var ioSession = ioService.createSession(channel.localAddress());
        channel.pipeline().addLast(ioSession.getHandler());

        // we now have an attached underlay, but it needs further processing before we expose it to the end user
        underlays.put(ioSession.getId(), underlayChannel);
    }

    // SessionListener integration. Responsible for observing authentication-related events, orchestrating both client
    // and server interactions.
    //
    // The state machine is responsible for driving TransportChannel

    //
    // At some point we should keep this in an encapsulated state object, but we have specializations, so we keep this
    // here at the cost of not modeling the solution domain correctly.

    @Override
    public final void sessionCreated(final Session session) {
        sessions.put(sessionId(session), session);
    }

    @Override
    public final void sessionException(final Session session, final Throwable throwable) {
        final var sessionId = sessionId(session);
        LOG.warn("Session {} encountered an error", sessionId, throwable);
        deleteSession(sessionId);
    }

    @Override
    public final void sessionDisconnect(final Session session, final int reason, final String msg,
            final String language, final boolean initiator) {
        final var sessionId = sessionId(session);
        LOG.debug("Session {} disconnected: {}", sessionId, SshConstants.getDisconnectReasonName(reason));
        deleteSession(sessionId);
    }

    @Override
    public final void sessionClosed(final Session session) {
        final var sessionId = sessionId(session);
        LOG.debug("Session {} closed", sessionId);
        deleteSession(sessionId);
    }

    @Override
    public final void sessionEvent(final Session session, final Event event) {
        final var sessionId = sessionId(session);
        switch (event) {
            case KeyEstablished -> {
                LOG.debug("New key established on session {}", sessionId);
                try {
                    onKeyEstablished(session);
                } catch (IOException e) {
                    LOG.error("Post-key step failed on session {}", sessionId, e);
                    deleteSession(sessionId);
                }
            }
            case Authenticated -> {
                LOG.debug("Authentication on session {} successful", sessionId);
                try {
                    onAuthenticated(session);
                } catch (IOException e) {
                    LOG.error("Post-authentication step failed on session {}", sessionId, e);
                    deleteSession(sessionId);
                }
            }
            case KexCompleted -> {
                LOG.debug("Key exchange completed on session {}", sessionId);
            }
            default -> {
                LOG.debug("Ignoring event {} on session {}", event, sessionId);
            }
        }
    }

    abstract void onKeyEstablished(Session session) throws IOException;

    abstract void onAuthenticated(Session session) throws IOException;

    final @NonNull SSHTransportChannel getUnderlayOf(final Long sessionId) throws IOException {
        final var ret = underlays.get(sessionId);
        if (ret == null) {
            throw new IOException("Cannot find underlay for " + sessionId);
        }
        return ret;
    }

    final void deleteSession(final Long sessionId) {
        sessions.remove(sessionId);
        // auth failure, close underlay if any
        completeUnderlay(sessionId, underlay -> underlay.channel().close());
    }

    // FIXME: this should be an assertion, the channel should just be there
    final void transportEstablished(final Long sessionId, final ChannelHandlerContext head) {
        completeUnderlay(sessionId, underlay -> {
            LOG.debug("Established transport on session {}", sessionId);
            addTransportChannel(new SSHTransportChannel(underlay));
            // Make sure any added handlers observe the channel being active
            head.fireChannelActive();
        });
    }

    private void completeUnderlay(final Long sessionId, final Consumer<TransportChannel> action) {
        final var removed = underlays.remove(sessionId);
        if (removed != null) {
            action.accept(removed);
        }
    }

    static final Long sessionId(final Session session) {
        return session.getIoSession().getId();
    }

    @VisibleForTesting
    Collection<Session> getSessions() {
        return sessions.values();
    }
}
