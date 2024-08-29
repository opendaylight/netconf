/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.nettyutil.NetconfChannel;
import org.opendaylight.netconf.nettyutil.NetconfChannelListener;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NetconfChannelListener} which initializes NETCONF server implementations working on top
 * of a {@link TransportChannel}.
 */
public final class ServerNetconfChannelListener extends NetconfChannelListener {
    private static final Logger LOG = LoggerFactory.getLogger(ServerNetconfChannelListener.class);
    private static final String DESERIALIZER_EX_HANDLER_KEY = "deserializerExHandler";

    private final NetconfServerSessionNegotiatorFactory negotiatorFactory;

    public ServerNetconfChannelListener(final NetconfServerSessionNegotiatorFactory negotiatorFactory) {
        this.negotiatorFactory = requireNonNull(negotiatorFactory);
    }

    @Override
    protected void onNetconfChannelEstablished(final NetconfChannel channel) {
        final var nettyChannel = channel.transport().channel();
        nettyChannel.pipeline()
            .addLast(DESERIALIZER_EX_HANDLER_KEY, new DeserializerExceptionHandler())
            .addLast(NETCONF_SESSION_NEGOTIATOR,
                // FIXME: use NetconfChannel
                negotiatorFactory.getSessionNegotiator(nettyChannel, nettyChannel.eventLoop().newPromise()));
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        LOG.error("Transport channel failed", cause);
    }
}
