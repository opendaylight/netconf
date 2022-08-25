/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.streams.SSESessionHandler;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class DeviceNotificationMountPointListener implements DOMMountPointListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationMountPointListener.class);

    private final ConcurrentMap<YangInstanceIdentifier, DeviceNotificationListenerAdaptor> listener =
        new ConcurrentHashMap<>();
    private ListenerRegistration<DOMMountPointListener> reg = null;

    private boolean isListeningOnMount() {
        return reg != null;
    }

    public synchronized void addDeviceNotificationListener(final YangInstanceIdentifier yangInstanceIdentifier,
            final DeviceNotificationListenerAdaptor listenerRegistration,
            final DOMMountPointService mountPointService) {
        listener.put(yangInstanceIdentifier, listenerRegistration);
        // Staring MountPoint listener
        if (!isListeningOnMount()) {
            reg = mountPointService.registerProvisionListener(this);
        }
    }

    private synchronized void resetListenerRegistration() {
        if (reg != null) {
            reg.close();
            reg = null;
        }
    }

    @PreDestroy
    public void close() {
        resetListenerRegistration();
        final Iterator<DeviceNotificationListenerAdaptor> it = listener.values().iterator();
        while (it.hasNext()) {
            try {
                it.next().close();
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Ignoring exception while closing device notification listener adaptor", e);
            }
            it.remove();
        }
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {

    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        final DeviceNotificationListenerAdaptor deviceNotificationListenerAdaptor = listener.remove(path);
        if (deviceNotificationListenerAdaptor != null) {
            deviceNotificationListenerAdaptor.getSubscribers().forEach(subscriber -> {
                if (subscriber.isConnected()) {
                    subscriber.sendDataMessage("Device disconnected");
                }
                if (subscriber instanceof SSESessionHandler) {
                    SSESessionHandler sseSessionHandler = (SSESessionHandler) subscriber;
                    try {
                        sseSessionHandler.close();
                    } catch (IllegalStateException e) {
                        LOG.warn("Ignoring exception while closing sse session");
                    }
                }
            });
            ListenersBroker.getInstance().removeAndCloseDeviceNotificationListener(deviceNotificationListenerAdaptor);
            // removing MountPoint listener if no Device notification is available
            if (listener.isEmpty()) {
                resetListenerRegistration();
            }
        }
    }

}