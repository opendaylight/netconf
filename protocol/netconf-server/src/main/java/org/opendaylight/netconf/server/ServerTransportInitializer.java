/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
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
    private static final String DESERIALIZER_EX_HANDLER_KEY = "deserializerExHandler";

    private final NetconfServerSessionNegotiatorFactory negotiatorFactory;

    public ServerTransportInitializer(final NetconfServerSessionNegotiatorFactory negotiatorFactory) {
        this.negotiatorFactory = requireNonNull(negotiatorFactory);
    }

    @Override
    public void onTransportChannelEstablished(final TransportChannel channel) {
        LOG.debug("Transport channel {} established", channel);
        final var nettyChannel = channel.channel();

        // FIXME: NETCONF-1106: Do not create this object. That requires figuring out why DeserializerExceptionHandler
        //                      is really needed on server-side, but not on client-side.
        new AbstractChannelInitializer<NetconfServerSession>() {
            @Override
            protected void initializeMessageDecoder(final Channel ch) {
                super.initializeMessageDecoder(ch);
                ch.pipeline().addLast(DESERIALIZER_EX_HANDLER_KEY, new DeserializerExceptionHandler());
            }

            @Override
            protected void initializeSessionNegotiator(final Channel ch, final Promise<NetconfServerSession> promise) {
                ch.pipeline().addAfter(DESERIALIZER_EX_HANDLER_KEY, NETCONF_SESSION_NEGOTIATOR,
                    negotiatorFactory.getSessionNegotiator(ch, promise));
            }
        }.initialize(nettyChannel, nettyChannel.eventLoop().newPromise());
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        LOG.error("Transport channel failed", cause);
    }
}
