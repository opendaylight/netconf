/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Filters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.filters.StreamFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public final class SubscriptionUtil {
    public static final YangInstanceIdentifier SUBSCRIPTIONS = YangInstanceIdentifier.of(
        YangInstanceIdentifier.NodeIdentifier.create(Subscriptions.QNAME),
        YangInstanceIdentifier.NodeIdentifier.create(Subscription.QNAME));
    public static final YangInstanceIdentifier STREAMS = YangInstanceIdentifier.of(
        YangInstanceIdentifier.NodeIdentifier.create(Streams.QNAME),
        YangInstanceIdentifier.NodeIdentifier.create(Stream.QNAME));
    public static final YangInstanceIdentifier FILTERS = YangInstanceIdentifier.of(
        YangInstanceIdentifier.NodeIdentifier.create(Filters.QNAME),
        YangInstanceIdentifier.NodeIdentifier.create(StreamFilter.QNAME));
    public static final QName QNAME_ID = QName.create(Subscription.QNAME, "id");
    public static final QName QNAME_STREAM = QName.create(Subscription.QNAME, "stream");
    public static final QName QNAME_STREAM_FILTER = QName.create(Subscription.QNAME, "stream-filter-name");
    public static final QName QNAME_STOP_TIME = QName.create(Subscription.QNAME, "stop-time");
    public static final QName QNAME_ENCODING = QName.create(Subscription.QNAME, "encoding");
    public static final QName QNAME_STREAM_NAME = QName.create(Stream.QNAME, "name");
    public static final QName QNAME_STREAM_FILTER_NAME = QName.create(StreamFilter.QNAME, "name");
    public static final QName QNAME_RECEIVER_NAME = QName.create(Receiver.QNAME, "name");
    public static final QName QNAME_RECEIVER_STATE = QName.create(Receiver.QNAME, "state");
    public static final QName QNAME_TARGET = QName.create(Subscription.QNAME, "target");

    private SubscriptionUtil() {
        // hidden on purpose
    }
}
