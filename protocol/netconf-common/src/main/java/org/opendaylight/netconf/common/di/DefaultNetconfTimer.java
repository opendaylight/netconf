/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.di;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.common.impl.NettyNetconfTimer;

/**
 * {@link NetconfTimer} implementation for reflective injection frameworks and POJO use.
 */
@Singleton
public final class DefaultNetconfTimer implements NetconfTimer, AutoCloseable {
    private final NettyNetconfTimer delegate;

    /**
     * Default constructor. Uses default values for both tick duration and wheel size.
     */
    @Inject
    public DefaultNetconfTimer() {
        this(100, TimeUnit.MILLISECONDS, 512);
    }

    public DefaultNetconfTimer(final long tickDuration, final TimeUnit unit, final int ticksPerWheel) {
        delegate = new NettyNetconfTimer(tickDuration, unit, ticksPerWheel);
    }

    @Override
    public Timeout newTimeout(final TimerTask task, final long delay, final TimeUnit unit) {
        return delegate.newTimeout(task, delay, unit);
    }

    @PreDestroy
    @Override
    public void close() {
        delegate.close();
    }

}
