/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;

public final class NetconfConnectorDTO implements AutoCloseable {
    private final @NonNull List<SchemaSourceRegistration<?>> yanglibRegistrations;
    private final @NonNull NetconfDeviceCommunicator communicator;
    private final @NonNull RemoteDeviceHandler facade;

    NetconfConnectorDTO(final NetconfDeviceCommunicator communicator, final RemoteDeviceHandler facade,
            final List<SchemaSourceRegistration<?>> yanglibRegistrations) {
        this.communicator = requireNonNull(communicator);
        this.facade = requireNonNull(facade);
        this.yanglibRegistrations = List.copyOf(yanglibRegistrations);
    }

    NetconfDeviceCommunicator getCommunicator() {
        return communicator;
    }

    NetconfClientSessionListener getSessionListener() {
        return communicator;
    }

    @Override
    public void close() {
        communicator.close();
        facade.close();
        yanglibRegistrations.forEach(SchemaSourceRegistration::close);
    }
}