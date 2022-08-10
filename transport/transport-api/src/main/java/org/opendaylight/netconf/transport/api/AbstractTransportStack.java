/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * Convenience base class for {@link TransportStack} implementations. It mediates atomic idempotence of
 * {@link #shutdown()}.
 */
public abstract class AbstractTransportStack implements TransportStack {
    private static final VarHandle SHUTDOWN;

    static {
        try {
            SHUTDOWN = MethodHandles.lookup()
                .findVarHandle(AbstractTransportStack.class, "shutdown", ListenableFuture.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile ListenableFuture<?> shutdown = null;

    @Override
    public final ListenableFuture<Empty> shutdown() {
        final var local = SettableFuture.<Empty>create();
        final var witness = (ListenableFuture<Empty>) SHUTDOWN.compareAndExchange(this, null, local);
        if (witness != null) {
            return witness;
        }
        local.setFuture(startShutdown());
        return local;
    }

    protected abstract @NonNull ListenableFuture<Empty> startShutdown();

    protected final boolean notShutdown() {
        return shutdown == null;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("shutdown", shutdown);
    }
}
