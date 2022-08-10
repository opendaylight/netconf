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
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * An SSH {@link TransportStack}. Instances of this class are built indirectly.
 */
public abstract sealed class SSHTransportStack implements TransportStack {
    static final class Connect extends SSHTransportStack {
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

    private static final VarHandle SHUTDOWN_PROMISE;

    static {
        try {
            SHUTDOWN_PROMISE = MethodHandles.lookup().findVarHandle(SSHTransportStack.class, "shutdownPromise",
                Future.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    private volatile Future<?> shutdownPromise;

    private SSHTransportStack() {
        // Hidden on purpose
    }

    @Override
    public Future<@NonNull Empty> shutdown() {
        final var local = ImmediateEventExecutor.INSTANCE.<Empty>newPromise();
        final var witness = (Future<@NonNull Empty>) SHUTDOWN_PROMISE.compareAndExchange(this, null, local);
        if (witness != null) {
            return witness;
        }

        final var shutdownFuture = startShutdown();
        shutdownFuture.addListener(future -> {
            if (future.isSuccess()) {
                local.setSuccess(Empty.value());
            } else {
                local.setFailure(future.cause());
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
