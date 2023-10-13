/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.server.session.SessionFactory;

/**
 * A {@link SessionFactory} tied to a {@link TransportSshServer}.
 */
final class TransportServerSessionFactory extends SessionFactory {
    TransportServerSessionFactory(final TransportSshServer server) {
        super(server);
    }

    @Override
    protected TransportServerSession doCreateSession(final IoSession ioSession) throws Exception {
        return new TransportServerSession((TransportSshServer) getServer(), ioSession);
    }
}
