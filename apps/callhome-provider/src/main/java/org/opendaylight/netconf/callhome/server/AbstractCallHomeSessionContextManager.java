/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractCallHomeSessionContextManager<T extends CallHomeSessionContext>
        implements CallHomeSessionContextManager<T> {
    protected final ConcurrentMap<String, T> contexts = new ConcurrentHashMap<>();
    private final CallHomeStatusRecorder statusRecorder;

    protected AbstractCallHomeSessionContextManager(final CallHomeStatusRecorder statusRecorder) {
        this.statusRecorder = requireNonNull(statusRecorder);
    }

    @Override
    public void register(final T context) {
        contexts.put(context.id(), context);
    }

    @Override
    public boolean exists(final String id) {
        return contexts.containsKey(id);
    }

    @Override
    public void remove(final String id) {
        final var context = contexts.remove(id);
        if (context != null) {
            context.close();
            if (!context.settableFuture().isDone()) {
                context.settableFuture().cancel(true);
            }
        }
    }

    @Override
    public void close() throws Exception {
        for (var it = contexts.entrySet().iterator(); it.hasNext(); ) {
            it.next().getValue().close();
            it.remove();
        }
    }
}
