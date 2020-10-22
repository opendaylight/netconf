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

public interface NetconfDeviceCommunicatorFactory {

    NativeNetconfDeviceCommunicator create(RemoteDeviceId id,
            RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> remoteDevice,
            UserPreferences netconfSessionPreferences, NetconfNode node);

    NativeNetconfDeviceCommunicator create(RemoteDeviceId id,
            RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> remoteDevice,
            NetconfNode node);

    NativeNetconfKeystore getKeystore();
}
