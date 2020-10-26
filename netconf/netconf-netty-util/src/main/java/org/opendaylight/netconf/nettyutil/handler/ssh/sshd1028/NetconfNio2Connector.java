/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.sshd1028;

import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.io.nio2.Nio2Connector;
import org.opendaylight.netconf.shaded.sshd.common.io.nio2.Nio2Session;

/**
 * Custom Nio2Connector which uses NetconfNio2Session instead of Nio2Session.
 * Should be removed when SSHD-1028 is fixed.
 */
public class NetconfNio2Connector extends Nio2Connector {

    public NetconfNio2Connector(final FactoryManager manager, final IoHandler handler,
                                final AsynchronousChannelGroup group) {
        super(manager, handler, group);
    }

    @Override
    protected Nio2Session createSession(final FactoryManager manager, final IoHandler handler,
                                        final AsynchronousSocketChannel socket) throws Throwable {
        return new NetconfNio2Session(this, manager, handler, socket, null);
    }
}
