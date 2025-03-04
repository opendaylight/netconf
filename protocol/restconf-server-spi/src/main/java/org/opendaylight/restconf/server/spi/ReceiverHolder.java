/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A custom receiver implementation that holds the subscription ID and receiver name,
 * and builds YangInstanceIdentifiers for counter leaves.
 */
public class ReceiverHolder {
    private static final Logger LOG = LoggerFactory.getLogger(ReceiverHolder.class);

    private final String subscriptionId;
    private final String receiverName;
    private final RestconfStream.Registry streamRegistry;
    private final AtomicLong sentEventCounter = new AtomicLong(0);

    public ReceiverHolder(final String subscriptionId, final String receiverName,
        final RestconfStream.Registry streamRegistry) {
        this.subscriptionId = Objects.requireNonNull(subscriptionId);
        this.receiverName = Objects.requireNonNull(receiverName);
        this.streamRegistry = Objects.requireNonNull(streamRegistry);
    }

    /**
     * Increments the sent-event-records counter and writes the updated value to the MD-SAL datastore.
     */
    public void incrementSentEventCounter() {
        final var counterValue = sentEventCounter.incrementAndGet();
        streamRegistry.updateReceiver(this, counterValue);
    }

    public String subscriptionId() {
        return subscriptionId;
    }

    public String receiverName() {
        return receiverName;
    }
}
