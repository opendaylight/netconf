/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.impl;

import java.util.regex.Matcher;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Path;
import org.glassfish.jersey.media.sse.EventOutput;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8639.layer.services.api.SubscribedNotifications;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.NotificationsHolder;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.RegisteredNotificationWrapper;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;

@Path("/")
public class SubscribedNotificationsImpl implements SubscribedNotifications {

    private final SchemaContextHandler schemaContextHandler;
    private final NotificationsHolder holder;

    public SubscribedNotificationsImpl(final SchemaContextHandler schemaContextHandler,
            final NotificationsHolder holder) {
        this.schemaContextHandler = schemaContextHandler;
        this.holder = holder;
    }

    @Override
    public EventOutput listen(final String streamName, final String subscriptionId,
            final HttpServletRequest httpServletRequest) {
        Uint32 subscriptionIdNum;
        try {
            subscriptionIdNum = Uint32.valueOf(subscriptionId);
        } catch (final NumberFormatException exception) {
            throw new RestconfDocumentedException("Invalid subscription-id in the request URI: "
                    + subscriptionId
                    + ". It must be a uint32 number.",
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.INVALID_VALUE,
                    exception);
        }

        final RegisteredNotificationWrapper notificationSubscription = this.holder.getNotification(subscriptionIdNum);
        if (notificationSubscription == null) {
            throw new RestconfDocumentedException("Notification stream subscription with id "
                    + subscriptionId
                    + " does not exist.",
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.INVALID_VALUE);
        }

        final NotificationDefinition streamSubscriptionNotificationDef = notificationSubscription
                .getSubscriptionNotificationListener().getNotificationDefinition();
        final String modulePrefixAndName = SubscribedNotificationsUtil.qNameToModulePrefixAndName(
                streamSubscriptionNotificationDef.getQName(), this.schemaContextHandler.get());

        final Matcher matcher = SubscribedNotificationsUtil.PREFIXED_NOTIFICATION_STREAM_NAME_PATTERN
                .matcher(streamName);
        if (!matcher.matches()) {
            throw new RestconfDocumentedException("Name of the notification stream in the request URI should be "
                    + "prefixed with the name of the module to which it belongs."
                    + "The correct form is module:notification."
                    + "In case of this subscription it should be: "
                    + modulePrefixAndName,
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.INVALID_VALUE);
        }

        if (!modulePrefixAndName.equals(streamName)) {
            throw new RestconfDocumentedException("Notification stream subscription with id "
                    + subscriptionId
                    + " does not belong to stream: '"
                    + streamName
                    + "'.",
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.INVALID_VALUE);
        }

        final EventOutput eventOutput = new EventOutput();
        notificationSubscription.getSubscriptionNotificationListener().addEventOutput(eventOutput);
        notificationSubscription.getSubscriptionNotificationListener().replayNotifications();
        return eventOutput;
    }
}
