/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NetconfSessionListenerFactory;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;

public final class ClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> {
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfSessionListenerFactory<NetconfClientSessionListener> sessionListenerFactory;

    public ClientChannelInitializer(final @NonNull NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final @NonNull NetconfSessionListenerFactory<NetconfClientSessionListener> sessionListenerFactory) {
        this.negotiatorFactory = requireNonNull(negotiatorFactory);
        this.sessionListenerFactory = requireNonNull(sessionListenerFactory);
    }

    @Override
    protected void initializeSessionNegotiator(final Channel ch, final Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, NETCONF_SESSION_NEGOTIATOR,
            negotiatorFactory.getSessionNegotiator(sessionListenerFactory, ch, promise));
        ch.config().setConnectTimeoutMillis((int) negotiatorFactory.getConnectionTimeoutMillis());
    }
}
