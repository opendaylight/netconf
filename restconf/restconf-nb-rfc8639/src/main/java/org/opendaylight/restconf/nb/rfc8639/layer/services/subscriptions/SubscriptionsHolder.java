/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Verify;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SubscriptionsHolder {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionsHolder.class);

    private final NavigableMap<Uint32, RegisteredNotificationWrapper> notifications = new ConcurrentSkipListMap<>();
    private final SubscriptionIdGenerator subscriptionIdGenerator;

    public SubscriptionsHolder(final SubscriptionIdGenerator subscriptionIdGenerator) {
        this.subscriptionIdGenerator = requireNonNull(subscriptionIdGenerator);
    }

    public Uint32 registerNotification(final RegisteredNotificationWrapper registeredNotification) {
        Uint32 subscriptionId = subscriptionIdGenerator.nextId();
        final RegisteredNotificationWrapper previousReg = notifications
                .putIfAbsent(subscriptionId, registeredNotification);
        if (previousReg != null) {
            LOG.warn("Registration ids conflict. Id {} has been already used for {}", subscriptionId, previousReg);
            synchronized (this) {
                subscriptionId = Uint32.valueOf(notifications.lastKey().intValue() + 1);
                Verify.verify(notifications.putIfAbsent(subscriptionId, registeredNotification) == null,
                        "Registration of establish subscription failed.");
            }
        }
        LOG.debug("Registration of {} has been completed successfully. Assigned id {}.",
                registeredNotification, subscriptionId);
        return subscriptionId;
    }

    public RegisteredNotificationWrapper getNotification(final Uint32 subscriptionId) {
        return notifications.get(subscriptionId);
    }

    public void removeNotification(final Uint32 subscriptionId) {
        notifications.remove(subscriptionId);
    }

    public boolean exists(final Uint32 subscriptionId) {
        return notifications.get(subscriptionId) != null;
    }

    public NavigableMap<Uint32, RegisteredNotificationWrapper> getNotifications() {
        return notifications;
    }
}
