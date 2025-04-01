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
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.OnCommitFutureCallback;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ForwardingRestconfStreamSubscription;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.subscription.SubscriptionUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.Receivers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MdsalRestconfStreamSubscription<T extends RestconfStream.Subscription>
        extends ForwardingRestconfStreamSubscription<T> {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfStreamSubscription.class);

    private final DOMDataBroker dataBroker;

    MdsalRestconfStreamSubscription(final T delegate, final DOMDataBroker dataBroker) {
        super(delegate);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    protected void terminateImpl(final ServerRequest<Empty> request, final QName terminationReason) {
        final var id = id();
        LOG.debug("{} terminated with reason {}", id, terminationReason);

        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, SubscriptionUtil.SUBSCRIPTIONS.node(
            NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, id)));
        tx.commit().addCallback(new OnCommitFutureCallback() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Removed subscription {} from operational datastore as of {}", id, result);
                delegate.terminate(request.transform(ignored -> Empty.value()), terminationReason);
            }

            @Override
            public void onFailure(final TransactionCommitFailedException cause) {
                LOG.warn("Failed to remove subscription {} from operational datastore", id, cause);
                request.completeWith(new RequestException(cause));
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Increments the sent-event-records counter and writes the updated value to the MD-SAL datastore.
     */
    @Override
    public void updateSentEventRecord() {
        delegate.updateSentEventRecord();
        final var counterValue = receiver().sentEventRecords();
        Futures.addCallback(updateReceiverSentEventRecord(),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.trace("Sent-event-records was updated {} for {} receiver on subscription {}",
                        counterValue, receiver().name(), id());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed update sent-event-records {} for {} receiver on subscription {}",
                        counterValue, receiver().name(), id(), cause);
                }
            }, MoreExecutors.directExecutor());
    }

    /**
     * Increments the excluded-event-records counter and writes the updated value to the MD-SAL datastore.
     */
    @Override
    public void updateExcludedEventRecord() {
        delegate.updateExcludedEventRecord();
        final var counterValue = receiver().excludedEventRecords();
        Futures.addCallback(updateReceiverExcludedEventRecord(),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final Void result) {
                    LOG.trace("Excluded-event-records was updated {} for {} receiver on subscription {}",
                        counterValue, receiver().name(), id());
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed update excluded-event-records {} for {} receiver on subscription {}",
                        counterValue, receiver().name(), id(), cause);
                }
            }, MoreExecutors.directExecutor());
    }

    /**
     * <p> Writes updated SentEventRecord counter for the receiver in subscription into datastore via a merge
     * operation.
     * Method returns a {@link ListenableFuture} that completes when the commit succeeds or fails.
     */
    private ListenableFuture<Void> updateReceiverSentEventRecord() {
        // Now issue a merge operation
        final var tx = dataBroker.newWriteOnlyTransaction();
        final var sentEventIid = YangInstanceIdentifier.builder()
            .node(NodeIdentifier.create(Subscriptions.QNAME))
            .node(NodeIdentifier.create(Subscription.QNAME))
            .node(NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_ID, id()))
            .node(NodeIdentifier.create(Receivers.QNAME))
            .node(NodeIdentifier.create(Receiver.QNAME))
            .node(NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_RECEIVER_NAME, receiver().name()))
            .node(NodeIdentifier.create(QNAME_SENT_EVENT_RECORDS));
        tx.merge(LogicalDatastoreType.OPERATIONAL, sentEventIid.build(), ImmutableNodes.leafNode(
            QNAME_SENT_EVENT_RECORDS, Uint64.valueOf(receiver().sentEventRecords())));
        return tx.commit().transform(unused -> null, MoreExecutors.directExecutor());
    }

    /**
     * <p> Writes updated ExcludedEventRecord counter for the receiver in subscription into datastore via a merge
     * operation.
     * Method returns a {@link ListenableFuture} that completes when the commit succeeds or fails.
     */
    private ListenableFuture<Void> updateReceiverExcludedEventRecord() {
        // Now issue a merge operation
        final var tx = dataBroker.newWriteOnlyTransaction();
        final var sentEventIid = YangInstanceIdentifier.builder()
            .node(NodeIdentifier.create(Subscriptions.QNAME))
            .node(NodeIdentifier.create(Subscription.QNAME))
            .node(NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_ID, id()))
            .node(NodeIdentifier.create(Receivers.QNAME))
            .node(NodeIdentifier.create(Receiver.QNAME))
            .node(NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_RECEIVER_NAME, receiver().name()))
            .node(NodeIdentifier.create(QNAME_EXCLUDED_EVENT_RECORDS));
        tx.merge(LogicalDatastoreType.OPERATIONAL, sentEventIid.build(), ImmutableNodes.leafNode(
            QNAME_EXCLUDED_EVENT_RECORDS, Uint64.valueOf(receiver().excludedEventRecords())));
        return tx.commit().transform(unused -> null, MoreExecutors.directExecutor());
    }
}
