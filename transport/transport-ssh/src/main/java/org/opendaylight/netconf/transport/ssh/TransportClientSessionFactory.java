/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.shaded.sshd.client.session.SessionFactory;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;

/**
 * A {@link SessionFactory} producing {@link TransportClientSession}s for a particular user.
 */
final class TransportClientSessionFactory extends SessionFactory {
    private final String username;

    TransportClientSessionFactory(final TransportSshClient client, final String username) {
        super(client);
        this.username = requireNonNull(username);
    }

    @Override
    protected TransportClientSession doCreateSession(final IoSession ioSession) throws Exception {
        final var ret = new TransportClientSession((TransportSshClient) getClient(), ioSession);
        ret.setUsername(username);
        return ret;
    }
}
