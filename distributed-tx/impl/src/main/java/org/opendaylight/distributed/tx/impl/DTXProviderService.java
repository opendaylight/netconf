/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.impl;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareConsumer;
import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.distributed.tx.api.DTx;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.api.DTxProvider;
import org.opendaylight.distributed.tx.spi.TxProvider;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DTXProviderService implements DTxProvider, AutoCloseable, BindingAwareConsumer{
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(DTXProviderService.class);
    TxProvider mountServiceProvider;
    TxProvider dataStoreServiceProvider;
    DTxProviderImpl dtxProviderImpl;
    final private Map<DTXLogicalTXProviderType, TxProvider> txProviderMap = new HashMap<>();

    public DTXProviderService(TxProvider msProvider, TxProvider dsProvider) {
        this.mountServiceProvider = msProvider;
        this.dataStoreServiceProvider = dsProvider;
        this.txProviderMap.put(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, this.dataStoreServiceProvider);
        this.txProviderMap.put(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER, this.mountServiceProvider);
        this.dtxProviderImpl = new DTxProviderImpl(this.txProviderMap);
    }

    @Nonnull
    @Override
    public DTx newTx(@Nonnull Set<InstanceIdentifier<?>> nodes) throws DTxException.DTxInitializationFailedException {
        return this.dtxProviderImpl.newTx(nodes);
    }

    @Nonnull
    @Override
    public DTx newTx(@Nonnull Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodes) throws DTxException.DTxInitializationFailedException {
        return this.dtxProviderImpl.newTx(nodes);
    }

    @Override
    public void close() throws Exception {
        this.mountServiceProvider= null;
        this.dataStoreServiceProvider= null;
    }

    @Override
    public void onSessionInitialized(BindingAwareBroker.ConsumerContext session){
    }
}

