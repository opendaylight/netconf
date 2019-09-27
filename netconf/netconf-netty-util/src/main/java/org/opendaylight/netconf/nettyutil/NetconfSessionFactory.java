/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import com.google.common.annotations.Beta;
import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.client.session.SessionFactory;
import org.apache.sshd.common.io.IoSession;

@Beta
public class NetconfSessionFactory extends SessionFactory {

    public NetconfSessionFactory(final ClientFactoryManager client) {
        super(client);
    }

    @Override
    protected ClientSessionImpl doCreateSession(final IoSession ioSession) throws Exception {
        return new ClientSessionImpl(getClient(), ioSession);
    }
}
