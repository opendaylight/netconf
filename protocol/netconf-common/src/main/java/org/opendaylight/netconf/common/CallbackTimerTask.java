/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common;

import static java.util.Objects.requireNonNull;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.common.NetconfTimer.TimeoutCallback;

record CallbackTimerTask(long started, @NonNull TimeoutCallback callback) implements TimerTask {
    CallbackTimerTask {
        requireNonNull(callback);
    }

    @Override
    public void run(final Timeout timeout) {
        callback.onTimeout(System.nanoTime() - started);
    }
}
