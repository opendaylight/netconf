/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ForwardingExecutorService;
import java.util.concurrent.ExecutorService;

/**
 * Facade for guarding against {@link #shutdown()} invocations. This is necessary as SSHD wants to shutdown the executor
 * when the server shuts down.
 */
final class ExecutorServiceFacade extends ForwardingExecutorService {
    private final ExecutorService delegate;

    ExecutorServiceFacade(final ExecutorService delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    protected ExecutorService delegate() {
        return delegate;
    }

    @Override
    public void shutdown() {
        // NO-OP
    }
}