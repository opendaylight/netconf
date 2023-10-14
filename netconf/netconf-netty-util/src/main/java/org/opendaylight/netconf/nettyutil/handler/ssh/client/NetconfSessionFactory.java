/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.session.SessionFactory;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;

/**
 * A {@link SessionFactory} which creates {@link NetconfClientSessionImpl}s.
 */
@Deprecated(since = "7.0.0", forRemoval = true)
public class NetconfSessionFactory extends SessionFactory {
    public NetconfSessionFactory(final ClientFactoryManager client) {
        super(client);
    }

    @Override
    protected NetconfClientSessionImpl doCreateSession(final IoSession ioSession) throws Exception {
        return new NetconfClientSessionImpl(getClient(), ioSession);
    }
}
