/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.security.PublicKey;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Handler of the sessions of an {@link SSHClient} which accepts connections from servers, such as in the Call-Home
 * case. It decides which servers are accepted and which credentials are used to authenticate to them.
 *
 * <p>Methods are invoked on SSH I/O threads and should not block.
 */
@NonNullByDefault
public interface SSHClientSessionHandler {
    /**
     * Check the key of a server which has connected. This is invoked once per session, before authentication. A later
     * key exchange on the same session does not invoke it again. It only checks that the server still presents the
     * key accepted here.
     *
     * @param session the session
     * @param serverKey the public key of the server
     * @param algorithms the algorithms negotiated on the session
     * @return the credentials to authenticate to this server, or {@code null} to reject it
     */
    @Nullable SSHClientCredentials checkServerKey(SSHClientSessionRef session, PublicKey serverKey,
        NegotiatedAlgorithms algorithms);

    /**
     * Invoked when a session has been closed.
     *
     * @param session the session
     */
    void sessionClosed(SSHClientSessionRef session);
}
