/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.api.NotificationTransformer;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming notifications. Either caches them(until onRemoteSchemaUp is called) or passes to sal Facade.
 */
final class NotificationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationHandler.class);

    private final RemoteDeviceHandler salFacade;
    // FIXME: better implementation?
    private final List<NetconfMessage> queue = new LinkedList<>();
    private final RemoteDeviceId id;

    private boolean passNotifications = false;
    private NotificationFilter filter;
    private NotificationTransformer messageTransformer;

    NotificationHandler(final RemoteDeviceHandler salFacade, final RemoteDeviceId id) {
        this.salFacade = requireNonNull(salFacade);
        this.id = requireNonNull(id);
    }

    synchronized void handleNotification(final NetconfMessage notification) {
        if (passNotifications) {
            passNotification(transformNotification(notification));
        } else {
            queueNotification(notification);
        }
    }

    /**
     * Forward all cached notifications and pass all notifications from this point directly to sal facade.
     * @param transformer Message transformer
     */
    synchronized void onRemoteSchemaUp(final NotificationTransformer transformer) {
        messageTransformer = requireNonNull(transformer);

        passNotifications = true;

        for (final NetconfMessage cachedNotification : queue) {
            passNotification(transformNotification(cachedNotification));
        }

        queue.clear();
    }

    private DOMNotification transformNotification(final NetconfMessage cachedNotification) {
        return checkNotNull(messageTransformer.toNotification(cachedNotification),
            "%s: Unable to parse received notification: %s", id, cachedNotification);
    }

    private void queueNotification(final NetconfMessage notification) {
        checkState(!passNotifications);

        LOG.debug("{}: Caching notification {}, remote schema not yet fully built", id, notification);
        if (LOG.isTraceEnabled()) {
            LOG.trace("{}: Caching notification {}", id, XmlUtil.toString(notification.getDocument()));
        }

        queue.add(notification);
    }

    private synchronized void passNotification(final DOMNotification parsedNotification) {
        LOG.debug("{}: Forwarding notification {}", id, parsedNotification);

        if (filter == null || filter.test(parsedNotification)) {
            salFacade.onNotification(parsedNotification);
        }
    }

    synchronized void addNotificationFilter(final NotificationFilter newFilter) {
        filter = newFilter;
    }

    synchronized void onRemoteSchemaDown() {
        queue.clear();
        passNotifications = false;
        messageTransformer = null;
    }

    @FunctionalInterface
    interface NotificationFilter extends Predicate<DOMNotification> {

    }
}
