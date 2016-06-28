/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.it.provider;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.distributed.tx.api.DTxProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.DistributedTxItModelService;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class DistributedTXItProvider implements BindingAwareProvider, AutoCloseable {
    private final org.slf4j.Logger log = LoggerFactory.getLogger(DistributedTXItProvider.class);
    private BindingAwareBroker.RpcRegistration<DistributedTxItModelService> dtxItModelService;
    DTxProvider dTxProvider;
    private DataBroker dataBroker;
    private MountPointService mountService;

    public  DistributedTXItProvider(DTxProvider provider){
        this.dTxProvider = provider;
    }
    @Override
    public void close() throws Exception {
        this.dtxItModelService = null;
    }

    @Override
    public void onSessionInitiated(BindingAwareBroker.ProviderContext session) {
        this.dataBroker = session.getSALService(DataBroker.class);
        this.mountService = session.getSALService(MountPointService.class);
        this.dtxItModelService = session.addRpcImplementation(DistributedTxItModelService.class, new DistributedTxProviderImpl(this.dTxProvider, this.dataBroker, this.mountService));
    }
}

