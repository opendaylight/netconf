/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SubscriptionHolder(
    // TODO: evaluate is this end up being useful, or maybe just ID would be enough.
    Subscription subscription,
    MdsalNotificationService mdsalNotificationService,
    SubscriptionStateService subscriptionStateService,
    SubscriptionStateMachine stateMachine) implements Registration {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHolder.class);

    @Override
    public void close() {
        final var id = subscription.getId().getValue();
        try {
            stateMachine.moveTo(id, SubscriptionState.END);
            subscriptionStateService.subscriptionTerminated(Instant.now().toString(),
                id.longValue(), "subscription-kill");
        } catch (InterruptedException e) {
            LOG.warn("Could not send subscription terminated notification: {}", e.getMessage());
        } catch (IllegalStateException | NoSuchElementException e) {
            LOG.warn("Could not move subscription to END state: {}", e.getMessage());
        }

        mdsalNotificationService.deleteSubscription(SubscriptionUtil.SUBSCRIPTIONS.node(
            YangInstanceIdentifier.NodeIdentifierWithPredicates.of(
                Subscription.QNAME, SubscriptionUtil.QNAME_ID, id)));
    }
}
