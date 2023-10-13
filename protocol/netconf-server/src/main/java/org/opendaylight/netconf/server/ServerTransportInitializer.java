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
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TransportChannelListener} which initializes NETCONF server implementations working on top
 * of a {@link TransportChannel}.
 */
public final class ServerTransportInitializer implements TransportChannelListener {
    private static final Logger LOG = LoggerFactory.getLogger(ServerTransportInitializer.class);

    private final ServerChannelInitializer initializer;

    public ServerTransportInitializer(final ServerChannelInitializer initializer) {
        this.initializer = requireNonNull(initializer);
    }

    @Override
    public void onTransportChannelEstablished(final TransportChannel channel) {
        LOG.debug("Transport channel {} established", channel);
        final var nettyChannel = channel.channel();
        initializer.initialize(nettyChannel, nettyChannel.eventLoop().newPromise());
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        LOG.error("Transport channel failed", cause);
    }
}
