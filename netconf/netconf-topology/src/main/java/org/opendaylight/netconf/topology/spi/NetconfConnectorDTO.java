/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import java.util.List;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.nativ.netconf.communicator.NativeNetconfDeviceCommunicator;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;

public final class NetconfConnectorDTO implements AutoCloseable {
    private final List<SchemaSourceRegistration<?>> yanglibRegistrations;
    private final NativeNetconfDeviceCommunicator communicator;
    private final RemoteDeviceHandler<NetconfSessionPreferences> facade;

    public NetconfConnectorDTO(
            final NativeNetconfDeviceCommunicator communicator,
            final RemoteDeviceHandler<NetconfSessionPreferences> facade,
            final List<SchemaSourceRegistration<?>> yanglibRegistrations) {
        this.communicator = communicator;
        this.facade = facade;
        this.yanglibRegistrations = yanglibRegistrations;
    }

    public NativeNetconfDeviceCommunicator getCommunicator() {
        return communicator;
    }

    public RemoteDeviceHandler<NetconfSessionPreferences> getFacade() {
        return facade;
    }

    public NetconfClientSessionListener getSessionListener() {
        return communicator;
    }

    @Override
    public void close() {
        communicator.close();
        facade.close();
        yanglibRegistrations.forEach(SchemaSourceRegistration::close);
    }
}