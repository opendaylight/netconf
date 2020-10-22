/*
 * Copyright (c) 2020 ... and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator;

import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.NativeNetconfKeystore;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.NativeNetconfKeystoreImpl;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;

public class NetconfDeviceCommunicatorFactoryImpl implements NetconfDeviceCommunicatorFactory {

    private final AAAEncryptionService encryptionService;
    private final EventExecutor eventExecutor;
    private final NativeNetconfKeystore nativeNetconfKeystore;
    private final NetconfClientDispatcher netconfClientDispatcher;

    public NetconfDeviceCommunicatorFactoryImpl(AAAEncryptionService encryptionService, EventExecutor eventExecutor,
            NetconfClientDispatcher netconfClientDispatcher) {
        this.encryptionService = encryptionService;
        this.eventExecutor = eventExecutor;
        this.nativeNetconfKeystore = new NativeNetconfKeystoreImpl();
        this.netconfClientDispatcher = netconfClientDispatcher;
    }

    @Override
    public NativeNetconfDeviceCommunicator create(final RemoteDeviceId id,
            final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> remoteDevice,
            final UserPreferences netconfSessionPreferences, final NetconfNode node) {
        return new NetconfDeviceCommunicator(id, remoteDevice, netconfSessionPreferences, node,
                netconfClientDispatcher, nativeNetconfKeystore, encryptionService, eventExecutor);
    }

    @Override
    public NativeNetconfDeviceCommunicator create(final RemoteDeviceId id,
            final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> remoteDevice,
            final NetconfNode node) {
        return new NetconfDeviceCommunicator(id, remoteDevice, node, netconfClientDispatcher,
                nativeNetconfKeystore, encryptionService, eventExecutor);
    }

    @Override
    public NativeNetconfKeystore getKeystore() {
        return nativeNetconfKeystore;
    }
}
