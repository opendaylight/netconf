/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.Map;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.yangtools.concepts.AbstractListenerRegistration;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceNotificationService implements DOMNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceNotificationService.class);

    private final Multimap<Absolute, DOMNotificationListener> listeners = HashMultimap.create();

    // Notification publish is very simple and hijacks the thread of the caller
    // TODO shouldnt we reuse the implementation for notification router from sal-broker-impl ?
    @SuppressWarnings("checkstyle:IllegalCatch")
    public synchronized void publishNotification(final DOMNotification notification) {
        for (final DOMNotificationListener domNotificationListener : listeners.get(notification.getType())) {
            try {
                domNotificationListener.onNotification(notification);
            } catch (final Exception e) {
                LOG.warn("Listener {} threw an uncaught exception during processing notification {}",
                        domNotificationListener, notification, e);
            }
        }
    }

    @Override
    public synchronized <T extends DOMNotificationListener> ListenerRegistration<T> registerNotificationListener(
            final T listener, final Collection<Absolute> types) {
        for (final Absolute type : types) {
            listeners.put(type, listener);
        }

        return new AbstractListenerRegistration<>(listener) {
            @Override
            protected void removeRegistration() {
                for (final Absolute type : types) {
                    listeners.remove(type, listener);
                }
            }
        };
    }

    @Override
    public synchronized <T extends DOMNotificationListener> ListenerRegistration<T> registerNotificationListener(
            final T listener, final Absolute... types) {
        return registerNotificationListener(listener, Lists.newArrayList(types));
    }

    @Override
    public synchronized Registration registerNotificationListeners(
            final Map<Absolute, DOMNotificationListener> typeToListener) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }
}
