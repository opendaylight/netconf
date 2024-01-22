/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import io.netty.util.concurrent.EventExecutor;
import java.math.BigDecimal;

@Deprecated
public final class TimedReconnectStrategyFactory implements ReconnectStrategyFactory {
    private final Long connectionAttempts;
    private final EventExecutor executor;
    private final double sleepFactor;
    private final int minSleep;
    private final long maxSleep;
    private final double jitter;

    public TimedReconnectStrategyFactory(final EventExecutor executor, final Long maxConnectionAttempts,
            final int minSleep, final BigDecimal sleepFactor, final long maxSleep, final double jitter) {
        if (maxConnectionAttempts != null && maxConnectionAttempts > 0) {
            connectionAttempts = maxConnectionAttempts;
        } else {
            connectionAttempts = null;
        }

        this.sleepFactor = sleepFactor.doubleValue();
        this.executor = executor;
        this.minSleep = minSleep;
        final long potentialMaxSleep = maxSleep >= minSleep ? maxSleep : minSleep;
        this.maxSleep = potentialMaxSleep;
        this.jitter = jitter;
    }

    @Override
    public ReconnectStrategy createReconnectStrategy() {
        return new TimedReconnectStrategy(executor, minSleep,
                minSleep, sleepFactor, maxSleep, connectionAttempts, null /*deadline*/, jitter);
    }
}