/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2024 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.function.Predicate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.api.NotificationTransformer;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceConnection;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming notifications. Either caches them(until onRemoteSchemaUp is called) or passes to sal Facade.
 */
@NonNullByDefault
abstract sealed class NotificationHandler {
    @FunctionalInterface
    interface NotificationFilter extends Predicate<DOMNotification> {

    }

    static final class Connected extends NotificationHandler {
        private final RemoteDeviceConnection connection;
        private final NotificationTransformer transformer;

        private Connected(final Connected prev, final NotificationFilter newFilter) {
            super(prev.id(), newFilter);
            connection = prev.connection;
            transformer = prev.transformer;
        }

        private Connected(final Disconnected prev, final RemoteDeviceConnection connection,
                final NotificationTransformer transformer) {
            super(prev);
            this.connection = requireNonNull(connection);
            this.transformer = requireNonNull(transformer);
        }

        @Override
        void onNotification(final NetconfMessage notification) {
            final var parsedNotification = checkNotNull(transformer.toNotification(notification),
                "%s: Unable to parse received notification: %s", id(), notification);

            LOG.debug("{}: Forwarding notification {}", id(), parsedNotification);
            if (matchesFilter(parsedNotification)) {
                connection.onNotification(parsedNotification);
            }
        }

        @Override
        Connected withFilter(final NotificationFilter newFilter) {
            return new Connected(this, newFilter);
        }
    }

    static final class Disconnected extends NotificationHandler {
        private final ArrayList<NetconfMessage> queue;

        private Disconnected(final Disconnected prev, final NotificationFilter newFilter) {
            super(prev.id(), newFilter);
            queue = prev.queue;
        }

        Disconnected(final RemoteDeviceId id) {
            super(id, null);
            queue = new ArrayList<>();
        }

        @Override
        void onNotification(final NetconfMessage notification) {
            LOG.debug("{}: Caching notification {}, remote schema not yet fully built", id(), notification);
            if (LOG.isTraceEnabled()) {
                LOG.trace("{}: Caching notification {}", id(), XmlUtil.toString(notification.getDocument()));
            }
            queue.add(notification);
        }

        @Override
        Disconnected withFilter(final NotificationFilter newFilter) {
            return new Disconnected(this, newFilter);
        }

        /**
         * Forward all cached notifications and pass all notifications from this point directly to the specified
         * connection.
         *
         * @param connection a {@link RemoteDeviceConnection}
         * @param transformer a {@link NotificationTransformer}
         */
        Connected toConnected(final RemoteDeviceConnection connection, final NotificationTransformer transformer) {
            final var ret = new Connected(this, connection, transformer);
            queue.forEach(ret::onNotification);
            queue.clear();
            return ret;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NotificationHandler.class);

    private final @Nullable NotificationFilter filter;
    private final RemoteDeviceId id;

    private NotificationHandler(final RemoteDeviceId id, final @Nullable NotificationFilter filter) {
        this.id = requireNonNull(id);
        this.filter = filter;
    }

    private NotificationHandler(final NotificationHandler prev) {
        id = prev.id;
        filter = prev.filter;
    }

    final RemoteDeviceId id() {
        return id;
    }

    abstract void onNotification(NetconfMessage notification);

    abstract NotificationHandler withFilter(NotificationFilter newFilter);

    final boolean matchesFilter(final DOMNotification notification) {
        final var local = filter;
        return local == null || local.test(notification);
    }
}
