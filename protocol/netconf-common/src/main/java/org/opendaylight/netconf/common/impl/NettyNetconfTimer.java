/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.opendaylight.netconf.common.NetconfTimer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple implementation of {@link NetconfTimer}, delegating to a {@link HashedWheelTimer}.
 */
@Component(service = NetconfTimer.class, configurationPid = "org.opendaylight.netconf.timer")
@Designate(ocd = NettyNetconfTimer.Configuration.class)
public final class NettyNetconfTimer implements NetconfTimer, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NettyNetconfTimer.class);
    private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
        .setNameFormat("netconf-timer-%d")
        .setDaemon(true)
        .build();

    /**
     * Configuration of {@link NettyNetconfTimer}.
     */
    @ObjectClassDefinition
    public @interface Configuration {
        /**
         * Return the duration of each timer tick in milliseconds.
         *
         * @return the duration of each timer tick in milliseconds
         */
        @AttributeDefinition(description = "Duration of each timer tick in milliseconds", min = "1")
        long tick$_$duration$_$_millis() default 100;

        /**
         * Return the size of the timer wheel.
         *
         * @return the size of the timer wheel
         */
        @AttributeDefinition(description = "The size of the timer wheel", min = "1", max = "1073741824")
        int ticks$_$per$_$wheel() default 512;
    }

    private HashedWheelTimer delegate;

    @Override
    public Timeout newTimeout(final TimerTask task, final long delay, final TimeUnit unit) {
        final var local = delegate;
        if (local == null) {
            throw new IllegalStateException("Timer has already been stopped");
        }
        return local.newTimeout(requireNonNull(task), delay, requireNonNull(unit));
    }


    /**
     * Default constructor.
     *
     * @param tickDuration the duration between tick
     * @param unit the time unit of the {@code tickDuration}
     * @param ticksPerWheel the size of the wheel
     */
    public NettyNetconfTimer(final long tickDuration, final TimeUnit unit, final int ticksPerWheel) {
        delegate = new HashedWheelTimer(THREAD_FACTORY, tickDuration, unit, ticksPerWheel);
        LOG.info("NETCONF timer started");
    }

    /**
     * OSGi constructor. Uses values from supplied {@link Configuration}.
     *
     * @param config the configuration
     */
    @Activate
    public NettyNetconfTimer(final Configuration config) {
        this(config.tick$_$duration$_$_millis(), TimeUnit.MILLISECONDS, config.ticks$_$per$_$wheel());
    }

    @Override
    @Deactivate
    public void close() {
        if (delegate != null) {
            delegate.stop();
            delegate = null;
            LOG.info("NETCONF timer stopped");
        }
    }
}
