/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import java.net.SocketAddress;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoService;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoSession;

/**
 * A utility class for accessing {@link NettyIoChannelHandler}'s {@code adapter} field, allowing us to reuse a bit of
 * shared code.
 */
final class NettyIoChannelHandler extends NettyIoSession {
    record HandlerAndId(@NonNull ChannelHandler handler, long id) {
        HandlerAndId {
            requireNonNull(handler);
        }
    }

    private NettyIoChannelHandler(final NettyIoService service, final IoHandler handler,
            final SocketAddress acceptanceAddress) {
        super(service, handler, acceptanceAddress);
    }

    @SuppressWarnings("resource")
    static HandlerAndId createAdapter(final FactoryManager factoryManager, final IoHandler sessionFactory,
            final Channel channel, final String groupName) {
        final var ioService = new SshIoService(factoryManager, sessionFactory);
        final var instance = new NettyIoChannelHandler(ioService, sessionFactory, channel.localAddress());
        return new HandlerAndId(instance.adapter, instance.id);
    }
}
