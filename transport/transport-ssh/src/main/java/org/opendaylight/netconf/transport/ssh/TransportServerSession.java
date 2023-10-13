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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.server.session.ServerSessionImpl;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A {@link ServerSessionImpl}, bound to a backend Netty channel.
 */
final class TransportServerSession extends ServerSessionImpl {
    private final SettableFuture<Empty> subsystemFuture = SettableFuture.create();

    private TransportChannel underlay;
    private String subsystemName;

    TransportServerSession(final TransportSshServer server, final IoSession ioSession) throws Exception {
        super(server, ioSession);
    }

    ListenableFuture<Empty> attachUnderlay(final String subsystem, final TransportChannel underlayTransport) {
        subsystemName = requireNonNull(subsystem);
        underlay = requireNonNull(underlayTransport);
        return subsystemFuture;
    }

    @Nullable TransportServerSubsystem openSubsystem(final String subsystem) {
        if (subsystem.equals(subsystemName)) {
            final var ret = new TransportServerSubsystem(subsystem, underlay);
            subsystemFuture.set(Empty.value());
            return ret;
        }
        return null;
    }
}
