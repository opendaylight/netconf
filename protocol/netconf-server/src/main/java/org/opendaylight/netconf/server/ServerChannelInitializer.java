/*
 * Copyright (c) 2013 Cisco Systems, Inc and others. All rights reserved.
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

public final class ServerChannelInitializer extends AbstractChannelInitializer<NetconfServerSession> {
    private static final String DESERIALIZER_EX_HANDLER_KEY = "deserializerExHandler";

    private final NetconfServerSessionNegotiatorFactory negotiatorFactory;

    public ServerChannelInitializer(final NetconfServerSessionNegotiatorFactory negotiatorFactory) {
        this.negotiatorFactory = requireNonNull(negotiatorFactory);
    }

    @Override
    protected void initializeMessageDecoder(final Channel ch) {
        super.initializeMessageDecoder(ch);
        ch.pipeline().addLast(DESERIALIZER_EX_HANDLER_KEY, new DeserializerExceptionHandler());
    }

    @Override
    protected void initializeSessionNegotiator(final Channel ch, final Promise<NetconfServerSession> promise) {
        ch.pipeline().addAfter(DESERIALIZER_EX_HANDLER_KEY, NETCONF_SESSION_NEGOTIATOR,
            negotiatorFactory.getSessionNegotiator(null, ch, promise));
    }
}
