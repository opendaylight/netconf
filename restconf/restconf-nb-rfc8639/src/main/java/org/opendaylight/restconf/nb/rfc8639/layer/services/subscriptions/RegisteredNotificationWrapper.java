/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import org.opendaylight.yangtools.concepts.ListenerRegistration;

public class RegisteredNotificationWrapper {

    private final NotificationStreamListener subscriptionNotificationListener;
    private final ListenerRegistration<?> notificationSubscriptionRegistration;

    RegisteredNotificationWrapper(final NotificationStreamListener subscriptionNotificationListener,
            final ListenerRegistration notificationSubscriptionRegistration) {
        this.subscriptionNotificationListener = subscriptionNotificationListener;
        this.notificationSubscriptionRegistration = notificationSubscriptionRegistration;
    }

    public NotificationStreamListener getSubscriptionNotificationListener() {
        return subscriptionNotificationListener;
    }

    public ListenerRegistration<?> getNotificationSubscriptionRegistration() {
        return notificationSubscriptionRegistration;
    }
}
