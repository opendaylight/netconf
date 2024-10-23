/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.spi.ForwardingDOMNotificationPublishService;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer.NetconfDeviceNotification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component
public class StateChangeNotifications {
    static DOMNotificationPublishService service;

    @Inject
    @Activate
    public StateChangeNotifications(DOMNotificationPublishService delegate) {
        service = new ForwardingDOMNotificationPublishService() {
            @Override
            protected DOMNotificationPublishService delegate() {
                return delegate;
            }
        };
    }

    public ListenableFuture<?> subscriptionStarted(ContainerNode node) throws InterruptedException {
        DOMNotification notification = new RestconfNotification(node);

        return service.putNotification(notification);
    }

//    public ListenableFuture<?> subscriptionModified(SubscriptionModified base) throws InterruptedException {
//        final var builder = new SubscriptionModifiedBuilder(base);
//        return service.putNotification((DOMNotification) builder.build());
//    }
//
//    public ListenableFuture<?> subscriptionTerminated(SubscriptionTerminated base) throws InterruptedException {
//        final var builder = new SubscriptionTerminatedBuilder(base);
//        return service.putNotification((DOMNotification) builder.build());
//    }
//
//    public ListenableFuture<?> subscriptionSuspended(SubscriptionSuspended base) throws InterruptedException {
//        final var builder = new SubscriptionSuspendedBuilder(base);
//        return service.putNotification((DOMNotification) builder.build());
//    }
//
//    public ListenableFuture<?> subscriptionResumed(SubscriptionResumed base) throws InterruptedException {
//        final var builder = new SubscriptionResumedBuilder(base);
//        return service.putNotification((DOMNotification) builder.build());
//    }
//
//    public ListenableFuture<?> subscriptionCompleted(SubscriptionCompleted base) throws InterruptedException {
//        final var builder = new SubscriptionCompletedBuilder(base);
//        return service.putNotification((DOMNotification) builder.build());
//    }
}