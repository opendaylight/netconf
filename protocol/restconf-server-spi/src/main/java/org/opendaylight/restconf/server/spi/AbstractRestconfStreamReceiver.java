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

/**
 * A custom receiver implementation that holds the subscription ID and receiver name.
 */
@NonNullByDefault
public abstract non-sealed class AbstractRestconfStreamReceiver implements RestconfStream.Receiver {
    private final String subscriptionId;
    private final String receiverName;
    private final AtomicLong sentEventCounter = new AtomicLong(0);
    private final AtomicLong excludedEventCounter = new AtomicLong(0);

    private State state;

    public AbstractRestconfStreamReceiver(final String subscriptionId, final String receiverName,
            final RestconfStream.Registry streamRegistry) {
        this.subscriptionId = requireNonNull(subscriptionId);
        this.receiverName = requireNonNull(receiverName);
        this.state = State.ACTIVE; // we only use Active state at the moment
    }

    @Override
    public String subscriptionId() {
        return subscriptionId;
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public void setState(State newState) {
        state = newState;
    }

    @Override
    public String receiverName() {
        return receiverName;
    }

    @Override
    public AtomicLong sentEventCounter() {
        return sentEventCounter;
    }

    @Override
    public AtomicLong excludedEventCounter() {
        return excludedEventCounter;
    }

    public enum RecordType {
        SENT_EVENT_RECORDS,
        EXCLUDED_EVENT_RECORDS
    }
}
