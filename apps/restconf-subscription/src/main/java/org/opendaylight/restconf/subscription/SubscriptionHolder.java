/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import java.util.NoSuchElementException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.NoSuchSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
final class SubscriptionHolder extends AbstractRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHolder.class);

    // TODO: evaluate is this end up being useful, or maybe just ID would be enough.
    private final Subscription subscription;
    private final MdsalNotificationService mdsalNotificationService;
    private final SubscriptionStateService subscriptionStateService;
    private final SubscriptionStateMachine stateMachine;

    SubscriptionHolder(final Subscription subscription, final MdsalNotificationService mdsalNotificationService,
            final SubscriptionStateService subscriptionStateService, final SubscriptionStateMachine stateMachine) {
        this.subscription = requireNonNull(subscription);
        this.mdsalNotificationService = requireNonNull(mdsalNotificationService);
        this.subscriptionStateService = requireNonNull(subscriptionStateService);
        this.stateMachine = requireNonNull(stateMachine);
    }

    @Override
    protected void removeRegistration() {
        final var id = subscription.getId().getValue();
        try {
            stateMachine.moveTo(id, SubscriptionState.END);
            subscriptionStateService.subscriptionTerminated(id, NoSuchSubscription.QNAME);
        } catch (InterruptedException e) {
            LOG.warn("Could not send subscription terminated notification", e);
        } catch (IllegalStateException | NoSuchElementException e) {
            LOG.warn("Could not move subscription to END state", e);
        }

        mdsalNotificationService.deleteSubscription(SubscriptionUtil.SUBSCRIPTIONS.node(
            YangInstanceIdentifier.NodeIdentifierWithPredicates.of(
                Subscription.QNAME, SubscriptionUtil.QNAME_ID, id)));
    }
}
