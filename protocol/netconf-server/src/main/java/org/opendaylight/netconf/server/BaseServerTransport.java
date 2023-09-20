/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.transport.api.TransportChannel;

/**
 * Abstract base class for NETCONF server implementations working on top of a {@link TransportChannel}.
 */
public class BaseServerTransport extends BaseTransportChannelListener {
    private final ServerChannelInitializer initializer;

    public BaseServerTransport(final ServerChannelInitializer initializer) {
        this.initializer = requireNonNull(initializer);
    }

    @Override
    public final void onTransportChannelEstablished(final TransportChannel channel) {
        super.onTransportChannelEstablished(channel);
        final var nettyChannel = channel.channel();
        initializer.initialize(nettyChannel, nettyChannel.eventLoop().newPromise());
    }
}
