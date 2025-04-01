/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jdt.annotation.NonNull;

public abstract non-sealed class ForwardingSubscriptionReceiver<T extends RestconfStream.Receiver> implements RestconfStream.Receiver {
    protected final @NonNull T delegate;

    protected ForwardingSubscriptionReceiver(final T delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void updateSentEventRecord() {
        delegate.updateSentEventRecord();
    }

    @Override
    public void updateExcludedEventRecord() {
        delegate.updateExcludedEventRecord();
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public RestconfStream.ReceiverState state() {
        return delegate.state();
    }

    @Override
    public AtomicLong sentEventRecords() {
        return delegate.sentEventRecords();
    }

    @Override
    public AtomicLong excludedEventRecords() {
        return delegate.excludedEventRecords();
    }
}
