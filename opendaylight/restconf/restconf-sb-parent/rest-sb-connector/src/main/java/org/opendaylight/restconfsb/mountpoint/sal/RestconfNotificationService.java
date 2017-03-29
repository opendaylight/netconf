/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import com.google.common.collect.HashMultimap;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class RestconfNotificationService implements DOMNotificationService, RestconfDeviceStreamListener {

    private final HashMultimap<SchemaPath, DOMNotificationListener> listeners = HashMultimap.create();

    @Override
    public void onNotification(final DOMNotification notification) {
        for (final DOMNotificationListener listener : listeners.get(notification.getType())) {
            listener.onNotification(notification);
        }
    }

    @Override
    public <T extends DOMNotificationListener> ListenerRegistration<T> registerNotificationListener(@Nonnull final T listener,
                                                                                                    @Nonnull final Collection<SchemaPath> types) {
        for (final SchemaPath type : types) {
            listeners.put(type, listener);
        }
        return new ListenerRegistration<T>() {
            @Override
            public void close() {
                for (final SchemaPath type : types) {
                    listeners.remove(type, listener);
                }
            }

            @Override
            public T getInstance() {
                return listener;
            }
        };
    }

    @Override
    public <T extends DOMNotificationListener> ListenerRegistration<T> registerNotificationListener(@Nonnull final T listener,
                                                                                                    final SchemaPath... types) {
        return registerNotificationListener(listener, Arrays.asList(types));
    }
}
