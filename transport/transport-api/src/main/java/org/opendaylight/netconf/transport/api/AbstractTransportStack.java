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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.util.concurrent.Future;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * Convenience base class for {@link TransportStack} implementations. It mediates atomic idempotence of
 * {@link #shutdown()}.
 *
 * @param <C> associated {@link TransportChannel} type
 */
public abstract class AbstractTransportStack<C extends TransportChannel> implements TransportStack {
    private final @NonNull TransportChannelListener listener;

    /**
     * Polymorphic state. It can be in one of four states:
     * <ol>
     *   <li>{@code null} when there is no attached transport channel,</li>
     *   <li>a {@link TransportChannel} instance when there is exactly one attached transport channel,</li>
     *   <li>a {@link Set} holding multiple attached transport channels,</li>
     *   <li>a {@link ListenableFuture} completing when shutdown is complete
     * </ol>
     */
    private Object state;

    protected AbstractTransportStack(final TransportChannelListener listener) {
        this.listener = requireNonNull(listener);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final ListenableFuture<Empty> shutdown() {
        final SettableFuture<Empty> future;
        final Set<TransportChannel> channels;

        synchronized (this) {
            var local = state;
            if (local instanceof ListenableFuture) {
                // Already shutting down, no-op
                return (ListenableFuture<Empty>) local;
            }

            if (local == null) {
                channels = Set.of();
            } else if (local instanceof Set) {
                channels = (Set<TransportChannel>) local;
            } else if (local instanceof TransportChannel tc) {
                channels = Set.of(tc);
            } else {
                throw new IllegalStateException("Unexpected state " + local);
            }
            state = future = SettableFuture.create();
        }

        final var futures = new ArrayList<ListenableFuture<?>>(channels.size() + 1);
        futures.add(startShutdown());
        for (var channel : channels) {
            futures.add(toListenableFuture(channel.channel().close()));
        }
        Futures.whenAllComplete(futures).run(() -> future.set(Empty.value()), MoreExecutors.directExecutor());
        return future;
    }

    protected abstract @NonNull ListenableFuture<Empty> startShutdown();

    protected final void addTransportChannel(final @NonNull C channel) {
        // Careful stepping here so we do not invoke callbacks while holding a lock...
        final var ch = channel.channel();
        if (add(channel)) {
            // The channel is tracked in state, make sure we remove it when it goes away. Invoke the user listener only
            // after that, so our listener fires first (and the user does not have a chance to close() before that).
            ch.closeFuture().addListener(ignored -> remove(channel));
            listener.onTransportChannelEstablished(channel);
        } else {
            // We are already shutting down, just close the channel
            ch.close();
        }
    }

    protected final void notifyTransportChannelFailed(final @NonNull Throwable cause) {
        listener.onTransportChannelFailed(cause);
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected synchronized ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("listener", listener).add("state", state);
    }

    protected static final @NonNull ListenableFuture<Empty> toListenableFuture(final Future<?> nettyFuture) {
        final var ret = SettableFuture.<Empty>create();
        nettyFuture.addListener(future -> {
            final var cause = future.cause();
            if (cause != null) {
                ret.setException(cause);
            } else {
                ret.set(Empty.value());
            }
        });
        return ret;
    }

    @SuppressWarnings("unchecked")
    private synchronized boolean add(final @NonNull TransportChannel channel) {
        final var local = state;
        if (local instanceof ListenableFuture) {
            // Already shutting down
            return false;
        }
        if (local == null) {
            // First session, simple
            state = channel;
        } else if (local instanceof Set) {
            ((Set<TransportChannel>) local).add(channel);
        } else if (local instanceof TransportChannel tc) {
            final var set = new HashSet<TransportChannel>(4);
            set.add(tc);
            set.add(channel);
            state = set;
        } else {
            throw new IllegalStateException("Unhandled state " + local);
        }
        return true;
    }

    private synchronized void remove(final @NonNull TransportChannel channel) {
        final var local = state;
        if (local == null || local instanceof ListenableFuture) {
            // No recorded channel or we are already shutting down
            return;
        }

        if (local.equals(channel)) {
            // Single channel, go back to null
            state = null;
        } else if (local instanceof Set<?> set) {
            // Multiple channels, let's just remove it. Note this does not collapse the set if there's just one
            // remaining session -- the chances are we will go back to more than one soon
            set.remove(channel);
        } else {
            throw new IllegalStateException("Unhandled state " + local);
        }
    }
}
