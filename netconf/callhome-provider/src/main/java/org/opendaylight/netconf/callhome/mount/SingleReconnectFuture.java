/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static java.util.Objects.requireNonNull;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.nettyutil.ReconnectFuture;
import org.opendaylight.yangtools.yang.common.Empty;

final class SingleReconnectFuture extends DefaultPromise<Empty> implements ReconnectFuture {
    private final Future<NetconfClientSession> sessionFuture;

    SingleReconnectFuture(final EventExecutor eventExecutor, final Future<NetconfClientSession> sessionFuture) {
        super(eventExecutor);
        this.sessionFuture = requireNonNull(sessionFuture);
        sessionFuture.addListener(future -> {
            if (!isDone()) {
                if (future.isCancelled()) {
                    cancel(false);
                } else if (future.isSuccess()) {
                    setSuccess(Empty.getInstance());
                } else {
                    setFailure(future.cause());
                }
            }
        });
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            if (!sessionFuture.isDone()) {
                sessionFuture.cancel(mayInterruptIfRunning);
            }
            return true;
        }
        return false;
    }

    @Override
    public Future<?> firstSessionFuture() {
        return sessionFuture;
    }
}
