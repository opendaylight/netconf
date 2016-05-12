/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.spiimpl;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareConsumer;
import org.opendaylight.distributed.tx.spi.TxProvider;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.Set;

/**
 * Data store transaction provider SPI which implements interface TxProvider.
 */
public class DataStoreTxProvider implements TxProvider, AutoCloseable, BindingAwareConsumer {
    private DataBroker dataBroker = null;

    @Nonnull
    @Override
    public ReadWriteTransaction newTx(@Nullable InstanceIdentifier<?> path) {
        return dataBroker.newReadWriteTransaction();
    }

    /* No lock for data store. */
    @Override
    public boolean isDeviceLocked(InstanceIdentifier<?> device) {
        return false;
    }

    @Override
    public boolean lockTransactionDevices(Set<InstanceIdentifier<?>> deviceSet) {
        return true;
    }

    @Override
    public void releaseTransactionDevices(Set<InstanceIdentifier<?>> deviceSet) {
    }

    @Override
    public void close() throws Exception {
        dataBroker = null;
    }

    @Override
    public void onSessionInitialized(final BindingAwareBroker.ConsumerContext session) {
        dataBroker = session.getSALService(DataBroker.class);
    }
}

