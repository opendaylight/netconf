/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * Abstract base class for {@link TransportStack}s overlaid on a different stack.
 */
public abstract class AbstractOverlayTransportStack<C extends TransportChannel> extends AbstractTransportStack<C> {
    private final @NonNull TransportChannelListener asListener = new TransportChannelListener<C>() {
        @Override
        public void onTransportChannelFailed(final Throwable cause) {
            notifyTransportChannelFailed(cause);
        }

        @Override
        public void onTransportChannelEstablished(@NonNull C channel) {
            onUnderlayChannelEstablished(channel);
        }
    };

    private volatile TransportStack underlay = null;

    protected AbstractOverlayTransportStack(final TransportChannelListener listener) {
        super(listener);
    }

    @Override
    protected final ListenableFuture<Empty> startShutdown() {
        return underlay.shutdown();
    }

    protected final @NonNull TransportChannelListener asListener() {
        return asListener;
    }

    protected abstract void onUnderlayChannelEstablished(@NonNull C underlayChannel);

    final void setUnderlay(final TransportStack underlay) {
        this.underlay = requireNonNull(underlay);
    }

    protected static final <T extends AbstractOverlayTransportStack<?>> @NonNull ListenableFuture<T> transformUnderlay(
            final T stack, final ListenableFuture<? extends TransportStack> tcpFuture) {
        return Futures.transform(tcpFuture, tcpStack -> {
            stack.setUnderlay(tcpStack);
            return stack;
        }, MoreExecutors.directExecutor());
    }
}
