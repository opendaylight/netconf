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
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;

public final class NetconfConnectorDTO implements AutoCloseable {
    private final List<SchemaSourceRegistration<?>> yanglibRegistrations;
    private final NetconfDeviceCommunicator communicator;
    private final RemoteDeviceHandler facade;

    public NetconfConnectorDTO(final NetconfDeviceCommunicator communicator, final RemoteDeviceHandler facade,
            final List<SchemaSourceRegistration<?>> yanglibRegistrations) {
        this.communicator = communicator;
        this.facade = facade;
        this.yanglibRegistrations = yanglibRegistrations;
    }

    public NetconfDeviceCommunicator getCommunicator() {
        return communicator;
    }

    public RemoteDeviceHandler getFacade() {
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