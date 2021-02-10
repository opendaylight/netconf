/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test.endtoend;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.NcmountService;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EndtoendTestService {
    private static final Logger logger = LoggerFactory.getLogger(EndtoendTestService.class);
    private final RpcProviderService rpcProviderService;
    private final DataBroker dataBroker;
    private final MountPointService mountPointService;

    @Inject
    public EndtoendTestService(final @Reference RpcProviderService rpcProviderService,
                               final @Reference DataBroker dataBroker,
                               final @Reference MountPointService mountPointService) {
        this.rpcProviderService = rpcProviderService;
        this.dataBroker = dataBroker;
        this.mountPointService = mountPointService;
        rpcProviderService.registerRpcImplementation(NcmountService.class, new NcmountServiceImpl(dataBroker, mountPointService));
    }
}
