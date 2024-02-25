/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

final class ClientTransportChannelListener implements TransportChannelListener {
    private final @NonNull SettableFuture<NetconfClientSession> sessionFuture = SettableFuture.create();
    private final ClientChannelInitializer initializer;

    ClientTransportChannelListener(final ClientChannelInitializer initializer) {
        this.initializer = requireNonNull(initializer);
    }

    @NonNull ListenableFuture<NetconfClientSession> sessionFuture() {
        return sessionFuture;
    }

    @Override
    public void onTransportChannelEstablished(final TransportChannel channel) {
        final var nettyChannel = channel.channel();
        final var promise = nettyChannel.eventLoop().<NetconfClientSession>newPromise();
        initializer.initialize(nettyChannel, promise);
        promise.addListener(ignored -> {
            final var cause = promise.cause();
            if (cause != null) {
                sessionFuture.setException(cause);
            } else {
                sessionFuture.set(promise.getNow());
            }
        });
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        sessionFuture.setException(cause);
    }
}