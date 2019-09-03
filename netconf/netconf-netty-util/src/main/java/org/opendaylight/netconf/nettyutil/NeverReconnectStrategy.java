/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

/**
 * Utility ReconnectStrategy singleton, which will cause the reconnect process to always fail. This class is thred-safe.
 */
@Deprecated
public final class NeverReconnectStrategy implements ReconnectStrategy {
    private final EventExecutor executor;
    private final int timeout;

    public NeverReconnectStrategy(final EventExecutor executor, final int timeout) {
        checkArgument(timeout >= 0);
        this.executor = requireNonNull(executor);
        this.timeout = timeout;
    }

    @Override
    public Future<Void> scheduleReconnect(final Throwable cause) {
        return executor.newFailedFuture(new Throwable("Reconnect failed", cause));
    }

    @Override
    public void reconnectSuccessful() {
        // Nothing to do
    }

    @Override
    public int getConnectTimeout() {
        return timeout;
    }
}
