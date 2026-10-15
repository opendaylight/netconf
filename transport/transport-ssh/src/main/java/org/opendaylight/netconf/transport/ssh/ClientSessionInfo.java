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
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;

/**
 * An {@link SSHClientSessionRef} backed by a {@link ClientSession}.
 *
 * <p>A new instance is created for each call to {@link SSHClientSessionHandler#checkServerKey} and
 * {@link SSHClientSessionHandler#sessionClosed}. Instances for the same session are equal, because they compare the
 * {@link ClientSession} object itself.
 */
@NonNullByDefault
record ClientSessionInfo(ClientSession session) implements SSHClientSessionRef {
    ClientSessionInfo {
        requireNonNull(session);
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
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", SSHTransportStack.sessionId(session))
            .add("remoteAddress", remoteAddress())
            .add("authenticated", isAuthenticated())
            .toString();
    }
}
