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
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;

public final class ClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> {
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    public ClientChannelInitializer(final @NonNull NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final @NonNull NetconfClientSessionListener sessionListener) {
        this.negotiatorFactory = requireNonNull(negotiatorFactory);
        this.sessionListener = requireNonNull(sessionListener);
    }

    @Override
    protected void initializeSessionNegotiator(final Channel ch, final Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(MessageDecoder.HANDLER_NAME, NETCONF_SESSION_NEGOTIATOR,
            negotiatorFactory.getSessionNegotiator(sessionListener, ch, promise));
        ch.config().setConnectTimeoutMillis((int) negotiatorFactory.getConnectionTimeoutMillis());
    }
}
