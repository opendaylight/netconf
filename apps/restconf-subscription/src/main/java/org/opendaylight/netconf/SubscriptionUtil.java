/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import java.util.concurrent.atomic.AtomicLong;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public final class SubscriptionUtil {
    public static final YangInstanceIdentifier SUBSCRIPTIONS = YangInstanceIdentifier.of(
        YangInstanceIdentifier.NodeIdentifier.create(Subscriptions.QNAME),
        YangInstanceIdentifier.NodeIdentifier.create(Subscription.QNAME));
    public static final QName QNAME_ID = QName.create(Subscription.QNAME, "id");
    public static final QName QNAME_STREAM = QName.create(Subscription.QNAME, "stream");
    public static final QName QNAME_STRAM_FILTER = QName.create(Subscription.QNAME, "stream-filter-name");
    public static final QName QNAME_STOP_TIME = QName.create(Subscription.QNAME, "stop-time");
    public static final QName QNAME_ENCODING = QName.create(Subscription.QNAME, "encoding");

    private SubscriptionUtil() {
        // hidden on purpose
    }

    /**
     * Generates a new subscription ID.
     * This method guarantees thread-safe, unique subscription IDs.
     *
     * @return A new subscription ID.
     */
    public static long generateSubscriptionId(final AtomicLong id) {
        return id.getAndIncrement();
    }
}
