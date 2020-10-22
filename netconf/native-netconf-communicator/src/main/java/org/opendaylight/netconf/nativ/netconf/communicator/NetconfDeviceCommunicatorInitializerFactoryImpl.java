/*
 * Copyright (c) 2020 ... . and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator;

import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.NativeNetconfKeystoreImpl;

public class NetconfDeviceCommunicatorInitializerFactoryImpl implements NetconfDeviceCommunicatorInitializerFactory {

    private final NativeNetconfKeystoreImpl nativeNetconfKeystore;
    private final AAAEncryptionService encryptionService;
    private final EventExecutor eventExecutor;

    public NetconfDeviceCommunicatorInitializerFactoryImpl(final AAAEncryptionService encryptionService,
            final EventExecutor eventExecutor) {
        this.encryptionService = encryptionService;
        this.eventExecutor = eventExecutor;
        nativeNetconfKeystore = new NativeNetconfKeystoreImpl();
    }

    @Override
    public NetconfDeviceCommunicatorFactory init(NetconfClientDispatcher netconfClientDispatcher) {
        return new NetconfDeviceCommunicatorFactoryImpl(encryptionService, eventExecutor, nativeNetconfKeystore,
                netconfClientDispatcher);
    }
}
