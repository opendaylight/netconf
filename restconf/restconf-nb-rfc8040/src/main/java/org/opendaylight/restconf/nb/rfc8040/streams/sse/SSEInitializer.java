/*
 * Copyright (c) 2018 Lumina Networks, Inc. All rights reserved.
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

@Singleton
public class SSEInitializer {

    private final ScheduledExecutorService executorService;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    @Inject
    public SSEInitializer(final ScheduledThreadPool scheduledThreadPool, final SSEConfiguration configuration) {
        this.executorService = scheduledThreadPool.getExecutor();
        this.maximumFragmentLength = configuration.getMaximumFragmentLength();
        this.heartbeatInterval = configuration.getHeartbeatInterval();
    }

    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    public int getMaximumFragmentLength() {
        return maximumFragmentLength;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

}
