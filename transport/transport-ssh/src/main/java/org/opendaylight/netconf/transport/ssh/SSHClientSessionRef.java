/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.net.SocketAddress;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A reference to an SSH client session, as seen by an {@link SSHClientSessionHandler}.
 */
@NonNullByDefault
public interface SSHClientSessionRef {
    /**
     * Return the identifier of this session. Two {@link SSHClientSessionRef} objects for the same session have the same
     * identifier and are equal.
     *
     * @return the identifier of this session
     */
    long id();

    /**
     * Return the address of the remote peer.
     *
     * @return the address of the remote peer
     */
    SocketAddress remoteAddress();

    /**
     * Check whether this session has been authenticated.
     *
     * @return {@code true} if this session has been authenticated
     */
    boolean isAuthenticated();

    /**
     * Close this session immediately, if it is still open.
     */
    void close();
}
