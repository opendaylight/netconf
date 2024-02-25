/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

record ClientTransportChannelListener(
        SettableFuture<NetconfClientSession> future,
        ClientChannelInitializer initializer) implements TransportChannelListener {
    ClientTransportChannelListener {
        requireNonNull(future);
        requireNonNull(initializer);
    }

    @Override
    public void onTransportChannelEstablished(final TransportChannel channel) {
        final var nettyChannel = channel.channel();
        final var promise = nettyChannel.eventLoop().<NetconfClientSession>newPromise();
        initializer.initialize(nettyChannel, promise);
        promise.addListener(ignored -> {
            final var cause = promise.cause();
            if (cause != null) {
                future.setException(cause);
            } else {
                future.set(promise.getNow());
            }
        });
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        future.setException(cause);
    }
}