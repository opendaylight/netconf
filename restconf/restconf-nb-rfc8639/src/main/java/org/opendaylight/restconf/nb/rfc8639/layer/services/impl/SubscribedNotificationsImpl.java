/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.Path;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.media.sse.EventOutput;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8639.layer.services.api.SubscribedNotifications;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.RegisteredNotificationWrapper;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.SubscriptionsHolder;
import org.opendaylight.yangtools.yang.common.Uint32;

@Path("/")
public class SubscribedNotificationsImpl implements SubscribedNotifications {
    private final SubscriptionsHolder holder;

    public SubscribedNotificationsImpl(final SubscriptionsHolder holder) {
        this.holder = requireNonNull(holder);
    }

    @Override
    public EventOutput listen(final String streamName, final String subscriptionId, final UriInfo uriInfo) {
        final Uint32 subscriptionIdNum;
        try {
            subscriptionIdNum = Uint32.valueOf(subscriptionId);
        } catch (final NumberFormatException e) {
            throw new RestconfDocumentedException("Invalid subscription-id in the request URI: "
                    + subscriptionId + ". It must be a uint32 number.",
                    RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE, e);
        }

        final RegisteredNotificationWrapper notificationSubscription = holder.getNotification(subscriptionIdNum);
        if (notificationSubscription == null) {
            throw new RestconfDocumentedException("Notification stream subscription with id "
                    + subscriptionId + " does not exist.",
                    RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE);
        }

        final EventOutput eventOutput = new EventOutput();
        notificationSubscription.getSubscriptionNotificationListener().addEventOutput(eventOutput);
        notificationSubscription.getSubscriptionNotificationListener().replayNotifications();
        return eventOutput;
    }
}
