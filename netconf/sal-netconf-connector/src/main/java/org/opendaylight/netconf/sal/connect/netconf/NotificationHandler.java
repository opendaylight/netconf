/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import com.google.common.base.Preconditions;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming notifications. Either caches them(until onRemoteSchemaUp is called) or passes to sal Facade.
 */
final class NotificationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationHandler.class);

    private final RemoteDeviceHandler<?> salFacade;
    private final List<NetconfMessage> queue = new LinkedList<>();
    private final RemoteDeviceId id;
    private boolean passNotifications = false;

    private NotificationFilter filter;
    private MessageTransformer<NetconfMessage> messageTransformer;

    NotificationHandler(final RemoteDeviceHandler<?> salFacade, final RemoteDeviceId id) {
        this.salFacade = Preconditions.checkNotNull(salFacade);
        this.id = Preconditions.checkNotNull(id);
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
    synchronized void onRemoteSchemaUp(final MessageTransformer<NetconfMessage> transformer) {
        this.messageTransformer = Preconditions.checkNotNull(transformer);

        passNotifications = true;

        for (final NetconfMessage cachedNotification : queue) {
            passNotification(transformNotification(cachedNotification));
        }

        queue.clear();
    }

    private DOMNotification transformNotification(final NetconfMessage cachedNotification) {
        final DOMNotification parsedNotification = messageTransformer.toNotification(cachedNotification);
        Preconditions.checkNotNull(
                parsedNotification, "%s: Unable to parse received notification: %s", id, cachedNotification);
        return parsedNotification;
    }

    private void queueNotification(final NetconfMessage notification) {
        Preconditions.checkState(!passNotifications);

        LOG.debug("{}: Caching notification {}, remote schema not yet fully built", id, notification);
        if (LOG.isTraceEnabled()) {
            LOG.trace("{}: Caching notification {}", id, XmlUtil.toString(notification.getDocument()));
        }

        queue.add(notification);
    }

    private synchronized void passNotification(final DOMNotification parsedNotification) {
        LOG.debug("{}: Forwarding notification {}", id, parsedNotification);

        if (filter == null || filter.filterNotification(parsedNotification).isPresent()) {
            salFacade.onNotification(parsedNotification);
        }
    }

    synchronized void addNotificationFilter(final NotificationFilter newFilter) {
        this.filter = newFilter;
    }

    synchronized void onRemoteSchemaDown() {
        queue.clear();
        passNotifications = false;
        messageTransformer = null;
    }

    interface NotificationFilter {

        Optional<DOMNotification> filterNotification(DOMNotification notification);
    }
}
