/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.nettyutil.NetconfChannel;
import org.opendaylight.netconf.nettyutil.NetconfChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClientNetconfChannelListener extends NetconfChannelListener
        implements FutureListener<NetconfClientSession>, FutureCallback<TransportStack> {
    private static final Logger LOG = LoggerFactory.getLogger(ClientNetconfChannelListener.class);

    private final @NonNull SettableFuture<NetconfClientSession> sessionFuture = SettableFuture.create();
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    ClientNetconfChannelListener(final NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final NetconfClientSessionListener sessionListener) {
        this.negotiatorFactory = requireNonNull(negotiatorFactory);
        this.sessionListener = requireNonNull(sessionListener);
    }

    @NonNull ListenableFuture<NetconfClientSession> sessionFuture() {
        return sessionFuture;
    }

    @Override
    protected void onNetconfChannelEstablished(final NetconfChannel channel) {
        LOG.debug("Initializing established transport {}", channel);
        final var nettyChannel = channel.transport().channel();
        final var promise = nettyChannel.eventLoop().<NetconfClientSession>newPromise();

        nettyChannel.pipeline().addLast(NETCONF_SESSION_NEGOTIATOR,
            negotiatorFactory.getSessionNegotiator(sessionListener, nettyChannel, promise));
        nettyChannel.config().setConnectTimeoutMillis((int) negotiatorFactory.getConnectionTimeoutMillis());

        promise.addListener(this);
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        sessionFuture.setException(cause);
    }

    @Override
    public void operationComplete(final Future<NetconfClientSession> future) throws Exception {
        final var cause = future.cause();
        if (cause != null) {
            sessionFuture.setException(cause);
        } else {
            sessionFuture.set(future.getNow());
        }
    }

    @Override
    public void onSuccess(final TransportStack result) {
        // No-op
    }

    @Override
    public void onFailure(final Throwable cause) {
        onTransportChannelFailed(cause);
    }
}