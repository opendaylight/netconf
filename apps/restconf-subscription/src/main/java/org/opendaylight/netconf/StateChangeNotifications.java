/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.spi.ForwardingDOMNotificationPublishService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompletedBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionModifiedBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionResumedBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionStarted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionStartedBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionSuspendedBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminatedBuilder;

public class StateChangeNotifications {
    DOMNotificationPublishService service = new ForwardingDOMNotificationPublishService() {
        @Override
        protected DOMNotificationPublishService delegate() {

        }
    };

    public ListenableFuture<?> subscriptionStarted(SubscriptionStarted base) throws InterruptedException {
        final var builder = new SubscriptionStartedBuilder(base);
//        builder.setId(null)
//            .setTarget(null)
//            .setStopTime(null)
//            .setWeighting(null)
//            .setDependency(null)
//            .setTransport(null)
//            .setEncoding(null)
//            .setPurpose(null);
        return service.putNotification((DOMNotification) builder.build());
    }

    public ListenableFuture<?> subscriptionModified(SubscriptionModified base) throws InterruptedException {
        final var builder = new SubscriptionModifiedBuilder(base);
        return service.putNotification((DOMNotification) builder.build());
    }

    public ListenableFuture<?> subscriptionTerminated(SubscriptionTerminated base) throws InterruptedException {
        final var builder = new SubscriptionTerminatedBuilder(base);
        return service.putNotification((DOMNotification) builder.build());
    }

    public ListenableFuture<?> subscriptionSuspended(SubscriptionSuspended base) throws InterruptedException {
        final var builder = new SubscriptionSuspendedBuilder(base);
        return service.putNotification((DOMNotification) builder.build());
    }

    public ListenableFuture<?> subscriptionResumed(SubscriptionResumed base) throws InterruptedException {
        final var builder = new SubscriptionResumedBuilder(base);
        return service.putNotification((DOMNotification) builder.build());
    }

    public ListenableFuture<?> subscriptionCompleted(SubscriptionCompleted base) throws InterruptedException {
        final var builder = new SubscriptionCompletedBuilder(base);
        return service.putNotification((DOMNotification) builder.build());
    }
}