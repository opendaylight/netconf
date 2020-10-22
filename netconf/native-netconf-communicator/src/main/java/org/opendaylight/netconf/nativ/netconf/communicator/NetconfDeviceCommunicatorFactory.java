/*
 * Copyright (c) 2020 ... and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator;

import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.NativeNetconfKeystore;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;

/**
 * Factory for creating of the native communicator with Netconf devices.
 *
 */
public interface NetconfDeviceCommunicatorFactory {

    /**
     * Creating native Netconf communicator with specific Netconf session preferences.
     *
     * @param id                        remove device id
     * @param remoteDevice              mediator between the communicator and MD-SAL
     * @param netconfSessionPreferences Netconf session preferences
     * @param node                      Netconf node
     * @return communicator
     */
    NativeNetconfDeviceCommunicator create(RemoteDeviceId id,
            RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> remoteDevice,
            UserPreferences netconfSessionPreferences, NetconfNode node);

    /**
     * Creating native Netconf communicator.
     *
     * @param id           remove device id
     * @param remoteDevice mediator between the communicator and MD-SAL
     * @param node         Netconf node
     * @return communicator
     */
    NativeNetconfDeviceCommunicator create(RemoteDeviceId id,
            RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> remoteDevice,
            NetconfNode node);

    /**
     * Get common keystore.
     *
     * @return keystore
     */
    NativeNetconfKeystore getKeystore();
}
