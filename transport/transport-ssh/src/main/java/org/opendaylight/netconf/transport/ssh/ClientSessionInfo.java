/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.net.SocketAddress;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;

/**
 * An {@link SSHClientSessionRef} backed by a {@link ClientSession}.
 */
@NonNullByDefault
final class ClientSessionInfo implements SSHClientSessionRef {
    private final ClientSession session;

    ClientSessionInfo(final ClientSession session) {
        this.session = requireNonNull(session);
    }

    @Override
    public long id() {
        return SSHTransportStack.sessionId(session);
    }

    @Override
    public SocketAddress remoteAddress() {
        return session.getRemoteAddress();
    }

    @Override
    public boolean isAuthenticated() {
        return session.isAuthenticated();
    }

    @Override
    public void close() {
        if (session.isOpen()) {
            session.close(true);
        }
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id());
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        return this == obj || obj instanceof ClientSessionInfo other && id() == other.id();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id())
            .add("remoteAddress", remoteAddress())
            .add("authenticated", isAuthenticated())
            .toString();
    }
}
