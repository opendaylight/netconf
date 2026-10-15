/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.session.SessionFactory;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;

/**
 * A {@link SessionFactory} producing {@link TransportClientSession}s. If a username is configured, it is set on each
 * session. Otherwise, the username is set later, e.g. by {@link SessionHandlerConfigurator} once the server is
 * accepted.
 */
final class TransportClientSessionFactory extends SessionFactory {
    private final @Nullable String username;

    TransportClientSessionFactory(final TransportSshClient client, final @Nullable String username) {
        super(client);
        this.username = username;
    }

    @Override
    protected TransportClientSession doCreateSession(final IoSession ioSession) throws Exception {
        final var ret = new TransportClientSession((TransportSshClient) getClient(), ioSession);
        if (username != null) {
            ret.setUsername(username);
        }
        return ret;
    }
}
