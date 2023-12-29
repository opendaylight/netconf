/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.server.session.ServerSessionImpl;
import org.opendaylight.netconf.transport.api.TransportChannel;

/**
 * A {@link ServerSessionImpl}, bound to a backend Netty channel.
 */
final class TransportServerSession extends ServerSessionImpl {
    private record State(String subsystem, TransportChannel underlay, SettableFuture<ChannelHandlerContext> future) {
        State {
            subsystem = requireNonNull(subsystem);
            underlay = requireNonNull(underlay);
            future = requireNonNull(future);
        }
    }

    private static final VarHandle STATE;

    static {
        try {
            STATE = MethodHandles.lookup().findVarHandle(TransportServerSession.class, "state", State.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile State state;

    TransportServerSession(final TransportSshServer server, final IoSession ioSession) throws Exception {
        super(server, ioSession);
    }

    ListenableFuture<ChannelHandlerContext> attachUnderlay(final String subsystem, final TransportChannel underlay) {
        final var newState = new State(subsystem, underlay, SettableFuture.create());
        final var witness = STATE.compareAndExchange(this, null, newState);
        if (witness != null) {
            throw new IllegalStateException("Already set up for " + witness);
        }
        return newState.future;
    }

    @Nullable TransportServerSubsystem openSubsystem(final String subsystem) {
        final var local = (State) STATE.getAndSet(this, null);
        if (local != null) {
            if (subsystem.equals(local.subsystem)) {
                return new TransportServerSubsystem(subsystem, local.underlay, local.future);
            }
            local.future.setException(new IOException("Mismatched subsystem " + subsystem));
        }
        return null;
    }
}
