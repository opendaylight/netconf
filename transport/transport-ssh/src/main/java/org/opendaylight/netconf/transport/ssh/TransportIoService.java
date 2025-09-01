/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import io.netty.channel.Channel;
import java.net.SocketAddress;
import java.util.concurrent.CancellationException;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoService;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactory;

final class TransportIoService extends NettyIoService {
    TransportIoService(final FactoryManager factoryManager, final IoHandler handler) {
        super((NettyIoServiceFactory) factoryManager.getIoServiceFactory(), handler, "sshd-transport-channels");
    }

    @Override
    protected void registerChannel(final Channel channel) throws CancellationException {
        // Required to keep things working, but does not need to be functional at all.
        //
        // Under normal circumstances, NettyIoService's group is used to track all open channels, so that we can close
        // them when we are shutdown.
        //
        // We do not need to do that, as we track the underlying transport instead, hence any channels tracked here will
        // disappear when we shut down the underlay.
        // Thus, we even do not need to check NettyIoService.noMoreSessions property.

        // no-op
    }

    TransportIoSession createSession(final SocketAddress acceptanceAddress) {
        return new TransportIoSession(this, handler, acceptanceAddress);
    }
}
