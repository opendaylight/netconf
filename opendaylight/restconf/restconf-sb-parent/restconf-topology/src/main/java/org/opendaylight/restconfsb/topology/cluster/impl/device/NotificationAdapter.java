/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;

/**
 * Bridge between {@link ProxyRestconfFacade} and {@link SlaveNotificationReceiver}.
 */
class NotificationAdapter implements RestconfDeviceStreamListener {


    private final List<RestconfDeviceStreamListener> listeners = new ArrayList<>();


    public void registerListener(final RestconfDeviceStreamListener listener) {
        listeners.add(listener);
    }

    @Override
    public void onNotification(final DOMNotification notification) {
        for (final RestconfDeviceStreamListener listener : listeners) {
            listener.onNotification(notification);
        }
    }

}
