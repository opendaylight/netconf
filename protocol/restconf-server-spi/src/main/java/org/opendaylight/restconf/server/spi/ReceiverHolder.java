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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.Receivers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.ZeroBasedCounter64;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;


/**
 * A custom receiver implementation that holds the subscription ID and receiver name,
 * and builds YangInstanceIdentifiers for counter leaves.
 */
public class ReceiverHolder {
    private static final QName QNAME_ID = QName.create(Subscription.QNAME, "id");
    private static final QName QNAME_RECEIVER_NAME = QName.create(Receiver.QNAME, "name");
    private static final QName QNAME_SENT_EVENT_RECORDS = QName.create(Receiver.QNAME, "sent-event-record");

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
