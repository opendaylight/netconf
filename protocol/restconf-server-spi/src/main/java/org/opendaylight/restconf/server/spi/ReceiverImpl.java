/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public final class ReceiverImpl implements RestconfStream.Receiver {
    private static final Logger LOG = LoggerFactory.getLogger(ReceiverImpl.class);

    private final String subscriptionId;
    private final String receiverName;
    private final RestconfStream.Registry streamRegistry;
    private final AtomicLong sentEventCounter = new AtomicLong(0);
    private final AtomicLong excludedEventCounter = new AtomicLong(0);

    private State state;

    public ReceiverImpl(final String subscriptionId, final String receiverName,
            final RestconfStream.Registry streamRegistry) {
        this.subscriptionId = requireNonNull(subscriptionId);
        this.receiverName = requireNonNull(receiverName);
        this.streamRegistry = requireNonNull(streamRegistry);
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

    public void updateSentEventRecord() {
        final var counterValue = sentEventCounter().incrementAndGet();
        Futures.addCallback(streamRegistry.updateReceiver(this, counterValue, RecordType.SENT_EVENT_RECORDS),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.trace("Sent-event-records was updated {} for {} receiver on subscription {}",
                        counterValue, receiverName(), subscriptionId());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed update sent-event-records {} for {} receiver on subscription {}",
                        counterValue, receiverName(), subscriptionId(), cause);
                }
            }, MoreExecutors.directExecutor());
    }

    /**
     * Increments the excluded-event-records counter and writes the updated value to the MD-SAL datastore.
     */
    public void updateExcludedEventRecord() {
        final var counterValue = excludedEventCounter().incrementAndGet();
        Futures.addCallback(streamRegistry.updateReceiver(this, counterValue, RecordType.EXCLUDED_EVENT_RECORDS),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.trace("Excluded-event-records was updated {} for {} receiver on subscription {}",
                        counterValue, receiverName(), subscriptionId());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed update excluded-event-records {} for {} receiver on subscription {}",
                        counterValue, receiverName(), subscriptionId(), cause);
                }
            }, MoreExecutors.directExecutor());
    }
}
