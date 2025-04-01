/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Objects;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamReceiver;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MdsalRestconfStreamReceiver extends AbstractRestconfStreamReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfStreamReceiver.class);
    private final RestconfStream.Registry streamRegistry;

    public MdsalRestconfStreamReceiver(String subscriptionId, String receiverName, RestconfStream.Registry streamRegistry) {
        super(subscriptionId, receiverName, streamRegistry);
        this.streamRegistry = Objects.requireNonNull(streamRegistry);
    }

    /**
     * Increments the sent-event-records counter and writes the updated value to the MD-SAL datastore.
     */
    public void updateSentEventRecord() {
        final var counterValue = sentEventCounter.incrementAndGet();
        Futures.addCallback(streamRegistry.updateReceiver(this, counterValue,
                AbstractRestconfStreamReceiver.RecordType.SENT_EVENT_RECORDS),
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
        final var counterValue = excludedEventCounter.incrementAndGet();
        Futures.addCallback(streamRegistry.updateReceiver(this, counterValue,
                AbstractRestconfStreamReceiver.RecordType.EXCLUDED_EVENT_RECORDS),
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
