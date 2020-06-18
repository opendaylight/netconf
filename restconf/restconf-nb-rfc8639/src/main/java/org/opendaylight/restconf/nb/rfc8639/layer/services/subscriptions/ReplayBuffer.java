/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import java.time.Instant;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// replay buffer for a specific notification stream
public class ReplayBuffer {
    private static final Logger LOG = LoggerFactory.getLogger(ReplayBuffer.class);

    private final NavigableMap<Instant, DOMNotification> timeStampToNotification;
    private final TransactionChainHandler transactionChainHandler;
    private final SchemaContext schemaContext;
    private final long replayBufferMaxSize;

    public ReplayBuffer(final long replayBufferMaxSize, final TransactionChainHandler transactionChainHandler,
            final SchemaContext schemaContext) {
        timeStampToNotification = new TreeMap<>();
        this.transactionChainHandler = transactionChainHandler;
        this.schemaContext = schemaContext;
        this.replayBufferMaxSize = replayBufferMaxSize;
    }

    public void addNotificationForTimeStamp(final Instant timeStamp, final DOMNotification notification) {
        if (timeStampToNotification.size() == 0) {
            writeReplayLogCreationToStreamInDatastore(timeStamp, notification);
        }

        if (timeStampToNotification.size() == replayBufferMaxSize) {
            final QName notificationQName = notification.getType().getLastComponent();
            LOG.info("Replay buffer for notification stream {} has reached its maximum size {}. Removing the oldest "
                            + "notification record ({}) from the buffer.", notificationQName, replayBufferMaxSize,
                    getOldestNotificationTimeStamp());
            timeStampToNotification.remove(getOldestNotificationTimeStamp());
            writeReplayLogCreationToStreamInDatastore(getOldestNotificationTimeStamp(), notification);
            LOG.info("replay-log-creation-time of notification stream {} was reset to {}.", notificationQName,
                    getOldestNotificationTimeStamp());
        }

        timeStampToNotification.put(timeStamp, notification);
    }

    public SortedMap<Instant, DOMNotification> getAllRecordedNotificationsFrom(final Instant fromTimeStamp) {
        return timeStampToNotification.tailMap(fromTimeStamp);
    }

    public SortedMap<Instant, DOMNotification> getAllRecordedNotificationsFromTo(final Instant fromTimeStamp,
            final Instant toTimeStamp) {
        return timeStampToNotification.subMap(fromTimeStamp, toTimeStamp);
    }

    public Instant getOldestNotificationTimeStamp() {
        return isEmpty() ? null : timeStampToNotification.firstKey();
    }

    public Instant getNewestNotificationTimeStamp() {
        return isEmpty() ? null : timeStampToNotification.lastKey();
    }

    public boolean isEmpty() {
        return timeStampToNotification.isEmpty();
    }

    private void writeReplayLogCreationToStreamInDatastore(final Instant replayLogCreationTime,
            final DOMNotification notification) {
        final DOMTransactionChain domTransactionChain = this.transactionChainHandler.get();
        final DOMDataTreeReadWriteTransaction transaction = domTransactionChain.newReadWriteTransaction();

        final YangInstanceIdentifier pathToReplayLogCreationTime = createPathToReplayLogCreationTimeLeaf(
                SubscribedNotificationsUtil.qNameToModulePrefixAndName(notification.getType().getLastComponent(),
                        schemaContext));

        final LeafNode<String> replayLogCreationTimeLeafNodeData = Builders.<String>leafBuilder().withNodeIdentifier(
                new NodeIdentifier(Rfc8040.MonitoringModule.LEAF_START_TIME_STREAM_QNAME)).withValue(
                        SubscribedNotificationsUtil.timeStampToRFC3339Format(replayLogCreationTime)).build();

        transaction.put(LogicalDatastoreType.OPERATIONAL,
                pathToReplayLogCreationTime, replayLogCreationTimeLeafNodeData);
        SubscribedNotificationsUtil.submitData(transaction, domTransactionChain);
    }

    private YangInstanceIdentifier createPathToReplayLogCreationTimeLeaf(final String prefixedStreamName) {
        return YangInstanceIdentifier.create(new NodeIdentifier(Rfc8040.MonitoringModule.CONT_RESTCONF_STATE_QNAME),
                new NodeIdentifier(Rfc8040.MonitoringModule.CONT_STREAMS_QNAME),
                new NodeIdentifier(Rfc8040.MonitoringModule.LIST_STREAM_QNAME),
                YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Rfc8040.MonitoringModule.LIST_STREAM_QNAME,
                        Rfc8040.MonitoringModule.LEAF_NAME_STREAM_QNAME, prefixedStreamName),
                new NodeIdentifier(Rfc8040.MonitoringModule.LEAF_START_TIME_STREAM_QNAME));
    }
}
