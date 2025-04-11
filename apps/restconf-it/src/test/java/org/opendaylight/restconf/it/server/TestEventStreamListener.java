/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.http.EventStreamListener;

public final class TestEventStreamListener implements EventStreamListener {
    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(5);

    private volatile boolean started = false;
    private volatile boolean ended = false;

    @Override
    public void onStreamStart() {
        started = true;
    }

    public boolean started() {
        return started;
    }

    @Override
    public void onEventField(@NonNull final String fieldName, @NonNull final String fieldValue) {
        if ("data".equals(fieldName)) {
            queue.add(fieldValue);
        }
    }

    public String readNext() throws InterruptedException {
        return queue.poll(5, TimeUnit.SECONDS);
    }

    @Override
    public void onStreamEnd() {
        ended = true;
    }

    boolean ended() {
        return ended;
    }
}