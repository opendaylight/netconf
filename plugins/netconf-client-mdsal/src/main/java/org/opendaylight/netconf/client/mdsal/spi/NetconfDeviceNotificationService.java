/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.yangtools.concepts.AbstractListenerRegistration;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceNotificationService implements DOMNotificationService {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceNotificationService.class);

    private final Multimap<Absolute, DOMNotificationListener> listeners = HashMultimap.create();

    // Notification publish is very simple and hijacks the thread of the caller
    // TODO: should we not reuse the implementation for notification router from mdsal-dom-broker ?
    @SuppressWarnings("checkstyle:IllegalCatch")
    public synchronized void publishNotification(final DOMNotification notification) {
        for (var listener : listeners.get(notification.getType())) {
            try {
                listener.onNotification(notification);
            } catch (Exception e) {
                LOG.warn("Listener {} threw an uncaught exception during processing notification {}", listener,
                    notification, e);
            }
        }
    }

    @Override
    public synchronized <T extends DOMNotificationListener> ListenerRegistration<T> registerNotificationListener(
            final T listener, final Collection<Absolute> types) {
        requireNonNull(listener);
        final var copy = types.stream().map(Objects::requireNonNull).distinct().toArray(Absolute[]::new);
        for (var type : copy) {
            listeners.put(type, listener);
        }

        return new AbstractListenerRegistration<>(listener) {
            @Override
            protected void removeRegistration() {
                synchronized (NetconfDeviceNotificationService.this) {
                    for (var type : copy) {
                        listeners.remove(type, listener);
                    }
                }
            }
        };
    }

    @Override
    public synchronized <T extends DOMNotificationListener> ListenerRegistration<T> registerNotificationListener(
            final T listener, final Absolute... types) {
        return registerNotificationListener(listener, Arrays.asList(types));
    }

    @Override
    public synchronized Registration registerNotificationListeners(
            final Map<Absolute, DOMNotificationListener> typeToListener) {
        final var copy = Map.copyOf(typeToListener);
        copy.forEach(listeners::put);

        return new AbstractRegistration() {
            @Override
            protected void removeRegistration() {
                synchronized (NetconfDeviceNotificationService.this) {
                    copy.forEach(listeners::remove);
                }
            }
        };
    }
}
