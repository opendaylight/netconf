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
import org.eclipse.jdt.annotation.NonNull;
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

public final class NetconfDeviceNotificationService implements DOMNotificationService {
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
    public <T extends DOMNotificationListener> ListenerRegistration<T> registerNotificationListener(final T listener,
            final Collection<Absolute> types) {
        final var lsnr = requireNonNull(listener);
        final var typesArray = types.stream().map(Objects::requireNonNull).distinct().toArray(Absolute[]::new);
        return switch (typesArray.length) {
            case 0 -> new AbstractListenerRegistration<>(lsnr) {
                @Override
                protected void removeRegistration() {
                    // No-op
                }
            };
            case 1 -> registerOne(lsnr, typesArray[0]);
            default -> registerMultiple(lsnr, typesArray);
        };
    }

    @Override
    public <T extends DOMNotificationListener> ListenerRegistration<T> registerNotificationListener(final T listener,
            final Absolute... types) {
        return registerNotificationListener(listener, Arrays.asList(types));
    }

    @Override
    public Registration registerNotificationListeners(final Map<Absolute, DOMNotificationListener> typeToListener) {
        final var copy = Map.copyOf(typeToListener);
        return switch (copy.size()) {
            case 0 -> () -> {
                // No-op
            };
            case 1 -> {
                final var entry = copy.entrySet().iterator().next();
                yield registerOne(entry.getValue(), entry.getKey());
            }
            default -> registerMultiple(copy);
        };
    }

    private synchronized <T extends DOMNotificationListener> @NonNull ListenerRegistration<T> registerOne(
            final @NonNull T listener, final Absolute type) {
        listeners.put(type, listener);
        return new AbstractListenerRegistration<>(listener) {
            @Override
            protected void removeRegistration() {
                synchronized (NetconfDeviceNotificationService.this) {
                    listeners.remove(type, getInstance());
                }
            }
        };
    }

    private synchronized <T extends DOMNotificationListener> @NonNull ListenerRegistration<T> registerMultiple(
            final @NonNull T listener, final Absolute[] types) {
        for (var type : types) {
            listeners.put(type, listener);
        }
        return new AbstractListenerRegistration<>(listener) {
            @Override
            protected void removeRegistration() {
                synchronized (NetconfDeviceNotificationService.this) {
                    for (var type : types) {
                        listeners.remove(type, getInstance());
                    }
                }
            }
        };
    }

    private synchronized @NonNull Registration registerMultiple(final Map<Absolute, DOMNotificationListener> toReg) {
        // we have at least two entries, which we will save as an array of 4 objects
        int idx = 0;
        final var array = new Object[toReg.size() * 2];
        for (var entry : toReg.entrySet()) {
            final var type = entry.getKey();
            final var listener = entry.getValue();

            listeners.put(type, listener);
            array[idx++] = type;
            array[idx++] = listener;
        }

        return new AbstractRegistration() {
            @Override
            protected void removeRegistration() {
                synchronized (NetconfDeviceNotificationService.this) {
                    for (int i = 0, length = array.length; i < length; ) {
                        final var type = array[i++];
                        final var listener = array[i++];
                        if (!listeners.remove(type, listener)) {
                            LOG.warn("Failed to remove {} listener {}, very weird", type, listener, new Throwable());
                        }
                    }
                }
            }
        };
    }
}
