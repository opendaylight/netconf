/*
 * Copyright (c) 2020 ... . and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.nativ.netconf.communicator.util.NetconfDeviceCapabilities;

/**
 * Common API for creating a connection and communicating with Netconf device just with using the
 * {@link NetconfMessage}.
 *
 */
public interface NativeNetconfDeviceCommunicator
        extends RemoteDeviceCommunicator<NetconfMessage>, NetconfClientSessionListener,
        RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> {

    /**
     * Initialize remote connection.
     *
     * @return future that returns success on first successful connection and failure when the underlying reconnecting
     *         strategy runs out of reconnection attempts
     */
    ListenableFuture<NetconfDeviceCapabilities> initializeRemoteConnection();

    /**
     * Disconnect session.
     */
    void disconnect();
}
