/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Set;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * Convenience base class for {@link TransportStack} implementations. It mediates atomic idempotence of
 * {@link #shutdown()}.
 */
public abstract class AbstractTransportStack<C extends TransportChannel> implements TransportStack {
    private final @NonNull TransportChannelListener listener;

    /**
     * Polymorphic state. It can be in one of four states:
     * <ol>
     *   <li>{@code null} when there is no attached transport channel,</li>
     *   <li>a {@link C} instance when there is exactly one attached transport channel,</li>
     *   <li>a concurrent {@link Set} holding multiple attached transport channels,</li>
     *   <li>a {@link ListenableFuture} completing when shutdown is complete
     * </ol>
     */
    @GuardedBy("this")
    private Object state;

    protected AbstractTransportStack(final @NonNull TransportChannelListener listener) {
        this.listener = requireNonNull(listener);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final ListenableFuture<Empty> shutdown() {
        final SettableFuture<Empty> future;
        final Set<C> channels;

        synchronized (this) {
            var local = state;
            if (local instanceof ListenableFuture) {
                // Already shutting down, no-op
                return (ListenableFuture<Empty>) local;
            }

            state = future = SettableFuture.create();
            if (local == null) {
                channels = Set.of();
            } else if (local instanceof Set) {
                channels = (Set<C>) local;
            } else if (local instanceof TransportChannel) {
                channels = Set.of((C) local);
            } else {
                throw new IllegalStateException("Unexpected state " + local);
            }
        }

        // FIXME: shutdown channels and collect their futures


        future.setFuture(startShutdown());
        return future;
    }

    protected abstract @NonNull ListenableFuture<Empty> startShutdown();

    protected final void onTransportChannelEstablished(final @NonNull C channel) {
        // FIXME: record channel
    }

    protected final void onTransportChannelFailed(final @NonNull Throwable cause) {
        listener.onTransportChannelFailed(cause);
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("listener", listener).add("state", state);
    }
}
