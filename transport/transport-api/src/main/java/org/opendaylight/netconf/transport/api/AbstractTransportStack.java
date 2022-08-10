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
import io.netty.util.concurrent.Future;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * Convenience base class for {@link TransportStack} implementations. It stores a {@link TransportChannelListener} and
 * mediates atomic idempotence of {@link #shutdown()}.
 */
public abstract class AbstractTransportStack implements TransportStack {
    private static final VarHandle SHUTDOWN;

    static {
        try {
            SHUTDOWN = MethodHandles.lookup()
                .findVarHandle(AbstractTransportStack.class, "shutdown", CompletableFuture.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull TransportChannelListener listener;

    private volatile CompletableFuture<?> shutdown = null;

    protected AbstractTransportStack(final @NonNull TransportChannelListener listener) {
        this.listener = requireNonNull(listener);
    }

    protected final @NonNull TransportChannelListener listener() {
        return listener;
    }

    @Override
    public final CompletionStage<Empty> shutdown() {
        final var local = new CompletableFuture<Empty>();
        final var witness = (CompletableFuture<Empty>) SHUTDOWN.compareAndExchange(this, null, local);
        if (witness != null) {
            return witness;
        }
        startShutdown().addListener(future -> {
            if (future.isSuccess()) {
                local.complete(Empty.value());
            } else {
                local.completeExceptionally(future.cause());
            }
        });
        return local;
    }

    protected abstract @NonNull Future<?> startShutdown();

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("listener", listener).add("shutdown", shutdown);
    }
}
