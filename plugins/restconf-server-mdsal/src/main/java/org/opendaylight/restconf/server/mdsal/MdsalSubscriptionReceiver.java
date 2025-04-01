/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.subscription.SubscriptionUtil.QNAME_EXCLUDED_EVENT_RECORDS;
import static org.opendaylight.restconf.subscription.SubscriptionUtil.QNAME_ID;
import static org.opendaylight.restconf.subscription.SubscriptionUtil.QNAME_RECEIVER_NAME;
import static org.opendaylight.restconf.subscription.SubscriptionUtil.QNAME_SENT_EVENT_RECORDS;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.server.spi.ForwardingSubscriptionReceiver;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.Receivers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class MdsalSubscriptionReceiver<T extends RestconfStream.Receiver> extends ForwardingSubscriptionReceiver<T> {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalSubscriptionReceiver.class);

    private final DOMDataBroker dataBroker;

    MdsalSubscriptionReceiver(final T delegate, final DOMDataBroker dataBroker) {
        super(delegate);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    public void updateSentEventRecord(Uint32 id) {
        final var counterValue = sentEventRecords().incrementAndGet();
        Futures.addCallback(updateReceiver(counterValue, RestconfStream.FilteredRecordType.SENT_EVENT_RECORDS, id),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.trace("Sent-event-records was updated to {} for receiver {} ",
                        counterValue, name());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed uto pdate sent-event-records to {} for receiver {}",
                        counterValue, name(), cause);
                }
            }, MoreExecutors.directExecutor());
    }

    /**
     * Increments the excluded-event-records counter and writes the updated value to the MD-SAL datastore.
     */
    @Override
    public void updateExcludedEventRecord(Uint32 id) {
        final var counterValue = excludedEventRecords().incrementAndGet();
        Futures.addCallback(updateReceiver(counterValue, RestconfStream.FilteredRecordType.EXCLUDED_EVENT_RECORDS, id),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.trace("Excluded-event-records was updated to {} for receiver {}",
                        counterValue, name());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed to update excluded-event-records to {} for receiver {}",
                        counterValue, name(), cause);
                }
            }, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Void> updateReceiver(final long counter,
        final RestconfStream.FilteredRecordType recordType, Uint32 id) {
        // Now issue a merge operation
        final var tx = dataBroker.newWriteOnlyTransaction();
        final var sentEventIid = YangInstanceIdentifier.builder()
            .node(YangInstanceIdentifier.NodeIdentifier.create(Subscriptions.QNAME))
            .node(YangInstanceIdentifier.NodeIdentifier.create(Subscription.QNAME))
            .node(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_ID, id))
            .node(YangInstanceIdentifier.NodeIdentifier.create(Receivers.QNAME))
            .node(YangInstanceIdentifier.NodeIdentifier.create(Receiver.QNAME))
            .node(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_RECEIVER_NAME,
                name()));

        final LeafNode<Uint64> counterValue;
        switch (recordType) {
            case SENT_EVENT_RECORDS -> {
                sentEventIid.node(YangInstanceIdentifier.NodeIdentifier.create(QNAME_SENT_EVENT_RECORDS));
                counterValue = ImmutableNodes.leafNode(
                    QNAME_SENT_EVENT_RECORDS, Uint64.valueOf(sentEventRecords().get()));
            }
            case EXCLUDED_EVENT_RECORDS -> {
                sentEventIid.node(YangInstanceIdentifier.NodeIdentifier.create(QNAME_EXCLUDED_EVENT_RECORDS));
                counterValue = ImmutableNodes.leafNode(
                    QNAME_EXCLUDED_EVENT_RECORDS, Uint64.valueOf(counter));
            }
            default -> throw new IllegalArgumentException("Unknown record type: " + recordType);
        }

        tx.merge(LogicalDatastoreType.OPERATIONAL, sentEventIid.build(), counterValue);
        return tx.commit().transform(unused -> null, MoreExecutors.directExecutor());
    }
}
