/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.base.MoreObjects;
import io.netty.util.concurrent.Future;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * An SSH {@link TransportStack}. Instances of this class are built indirectly.
 */
abstract sealed class SSHTransportStack implements TransportStack {
    static final class Initiate extends SSHTransportStack {
        @Override
        Future<?> startShutdown() {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }
    }

    static final class Listen extends SSHTransportStack {
        @Override
        Future<?> startShutdown() {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }
    }

    private static final VarHandle SHUTDOWN;

    static {
        try {
            SHUTDOWN = MethodHandles.lookup().findVarHandle(SSHTransportStack.class, "shutdown",
                CompletableFuture.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    private volatile CompletableFuture<?> shutdown;

    private SSHTransportStack() {
        // Hidden on purpose
    }

    @Override
    public CompletionStage<Empty> shutdown() {
        final var local = new CompletableFuture<Empty>();
        final var witness = (CompletableFuture<Empty>) SHUTDOWN.compareAndExchange(this, null, local);
        if (witness != null) {
            return witness;
        }

        final var shutdownFuture = startShutdown();
        shutdownFuture.addListener(future -> {
            if (future.isSuccess()) {
                local.complete(Empty.value());
            } else {
                local.completeExceptionally(future.cause());
            }
        });
        return local;
    }

    abstract Future<?> startShutdown();

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).toString();
    }
}
