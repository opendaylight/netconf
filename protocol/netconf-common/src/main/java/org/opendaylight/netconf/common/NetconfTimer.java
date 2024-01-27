/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common;

import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A NETCONF-specific equivalent of {@link Timer}. It specifically excludes {@link Timer#stop()} from the API surface.
 */
@NonNullByDefault
public interface NetconfTimer {
    /**
     * Schedules the specified {@link TimerTask} for one-time execution after the specified delay.
     * See {@link Timer#newTimeout(TimerTask, long, TimeUnit)}.
     *
     * @param task a TimerTask
     * @param delay delay to apply
     * @param unit a TimeUnit
     * @return a handle which is associated with the specified task
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalStateException if this timer has been stopped already
     * @throws RejectedExecutionException if the pending timeouts are too many and creating new timeout can cause
     *                                    instability in the system
     */
    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);
}
