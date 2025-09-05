/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.common.channel.ChannelFactory;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;

/**
 * A {@link ChannelFactory} used with {@link TransportServerSession}s.
 */
final class TransportChannelSessionFactory implements ChannelFactory {
    static final TransportChannelSessionFactory INSTANCE = new TransportChannelSessionFactory();

    private TransportChannelSessionFactory() {
        // Hidden on purpose
    }

    @Override
    public String getName() {
        // mimic ChannelSessionFactory without referencing it
        return "session";
    }

    @Override
    public TransportChannelSession createChannel(final Session session) throws IOException {
        return new TransportChannelSession(SSHTransportStack.checkCast(TransportServerSession.class, session));
    }
}
