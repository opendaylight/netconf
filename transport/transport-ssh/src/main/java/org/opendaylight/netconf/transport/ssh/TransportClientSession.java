/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSessionImpl;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;

/**
 * Our own version of {@link ClientSessionImpl}, bound to a backend Netty channel.
 */
final class TransportClientSession extends ClientSessionImpl {
    TransportClientSession(final TransportSshClient client, final IoSession ioSession) throws Exception {
        super(client, ioSession);
    }

    @Override
    public TransportClientSubsystem createSubsystemChannel(final String subsystem) throws IOException {
        final var channel = new TransportClientSubsystem(subsystem);
        final var service = getConnectionService();
        final var id = service.registerChannel(channel);
        if (log.isDebugEnabled()) {
            log.debug("createSubsystemChannel({})[{}] created id={}", this, subsystem, id);
        }
        return channel;
    }
}
