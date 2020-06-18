/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static java.util.Objects.requireNonNull;

import org.opendaylight.yangtools.concepts.ListenerRegistration;

public class RegisteredNotificationWrapper {

    private final NotificationStreamListener subscriptionNotificationListener;
    private final ListenerRegistration<?> notificationSubscriptionRegistration;

    RegisteredNotificationWrapper(final NotificationStreamListener subscriptionNotificationListener,
            final ListenerRegistration<NotificationStreamListener> notificationSubscriptionRegistration) {
        this.subscriptionNotificationListener = requireNonNull(subscriptionNotificationListener);
        this.notificationSubscriptionRegistration = requireNonNull(notificationSubscriptionRegistration);
    }

    public NotificationStreamListener getSubscriptionNotificationListener() {
        return subscriptionNotificationListener;
    }

    public ListenerRegistration<?> getNotificationSubscriptionRegistration() {
        return notificationSubscriptionRegistration;
    }
}
