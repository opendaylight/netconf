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
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public abstract non-sealed class AbstractSubscriptionReceiver implements RestconfStream.Receiver {
    private final String name;
    private final RestconfStream.ReceiverState state;
    private final AtomicLong sentEventRecords = new AtomicLong(0);
    private final AtomicLong excludedEventRecords = new AtomicLong(0);

    public AbstractSubscriptionReceiver(final String name, final RestconfStream.ReceiverState state) {
        this.name = requireNonNull(name);
        this.state = requireNonNull(state);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public RestconfStream.ReceiverState state() {
        return state;
    }

    @Override
    public AtomicLong sentEventRecords() {
        return sentEventRecords;
    }

    @Override
    public AtomicLong excludedEventRecords() {
        return excludedEventRecords;
    }
}
