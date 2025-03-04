/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom receiver implementation that holds the subscription ID and receiver name.
 */
public class ReceiverHolder {
    private static final Logger LOG = LoggerFactory.getLogger(ReceiverHolder.class);

    private final String subscriptionId;
    private final String name;
    private final RestconfStream.Registry streamRegistry;
    private final AtomicLong sentEventCounter = new AtomicLong(0);
    private final AtomicLong excludedEventCounter = new AtomicLong(0);

    public ReceiverHolder(final String subscriptionId, final String name,
            final RestconfStream.Registry streamRegistry) {
        this.subscriptionId = Objects.requireNonNull(subscriptionId);
        this.name = Objects.requireNonNull(name);
        this.streamRegistry = Objects.requireNonNull(streamRegistry);
    }

    /**
     * Increments the sent-event-records counter and writes the updated value to the MD-SAL datastore.
     */
    public void updateSentEventRecord() {
        sentEventCounter.incrementAndGet();
        Futures.addCallback(streamRegistry.updateReceiver(this, ReceiverHolder.RecordType.SENT_EVENT_RECORDS),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.trace("Sent-event-records was updated {} for {} receiver on subscription {}",
                        sentEventCounter().get(),
                        name(), subscriptionId());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed update sent-event-records {} for {} receiver on subscription {}",
                        sentEventCounter().get(),
                        name(), subscriptionId(),cause);
                }
            }, MoreExecutors.directExecutor());
    }

    /**
     * Increments the excluded-event-records counter and writes the updated value to the MD-SAL datastore.
     */
    public void updateExcludedEventRecord() {
        excludedEventCounter.incrementAndGet();
        Futures.addCallback(streamRegistry.updateReceiver(this, ReceiverHolder.RecordType.EXCLUDE_EVENT_RECORDS),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.trace("Excluded-event-records was updated {} for {} receiver on subscription {}",
                        sentEventCounter().get(),
                        name(), subscriptionId());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed update excluded-event-records {} for {} receiver on subscription {}",
                        sentEventCounter().get(),
                        name(), subscriptionId(),cause);
                }
            }, MoreExecutors.directExecutor());
    }

    public String subscriptionId() {
        return subscriptionId;
    }

    public String name() {
        return name;
    }

    public AtomicLong sentEventCounter() {
        return sentEventCounter;
    }

    public AtomicLong excludedEventCounter() {
        return excludedEventCounter;
    }

    public enum RecordType {
        SENT_EVENT_RECORDS,
        EXCLUDE_EVENT_RECORDS
    }
}
