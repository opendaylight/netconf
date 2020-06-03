/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.sse;

import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;

/**
 * Holder of configuration for SSE.
 *
 */
@Singleton
public class SSEInitializer {

    private final ScheduledExecutorService executorService;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    /**
     * Creation of the SSE initializer.
     *
     * @param scheduledThreadPool    ODL thread pool used for fetching of scheduled executors.
     * @param configuration          Connection configuration holder.
     */
    @Inject
    public SSEInitializer(final ScheduledThreadPool scheduledThreadPool, final Configuration configuration) {
        this.executorService = scheduledThreadPool.getExecutor();
        this.maximumFragmentLength = configuration.getMaximumFragmentLength();
        this.heartbeatInterval = configuration.getHeartbeatInterval();
    }

    /**
     * Getter for ScheduledExecutorService.
     */
    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Getter for Maximum Fragment Length.
     */
    public int getMaximumFragmentLength() {
        return maximumFragmentLength;
    }

    /**
     * Getter for Heartbeat Interval.
     */
    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

}
