/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import org.opendaylight.restconf.notification.mdsal.MdsalNotificationaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public record SubscriptionHolder(
    Subscription subscription,
    MdsalNotificationaService mdsalNotificationaService) implements Registration {

    private static final YangInstanceIdentifier SUBSCRIPTIONS = YangInstanceIdentifier.of(
        YangInstanceIdentifier.NodeIdentifier.create(Subscriptions.QNAME),
        YangInstanceIdentifier.NodeIdentifier.create(Subscription.QNAME));
    private static final QName QNAME_ID = QName.create(Subscription.QNAME, "id").intern();

    @Override
    public void close() {
        // TODO: subscription state change notification
        mdsalNotificationaService.deleteSubscription(SUBSCRIPTIONS.node(
            YangInstanceIdentifier.NodeIdentifierWithPredicates.of(
                Subscription.QNAME, QNAME_ID, subscription.getId().getValue().longValue())));
    }
}
