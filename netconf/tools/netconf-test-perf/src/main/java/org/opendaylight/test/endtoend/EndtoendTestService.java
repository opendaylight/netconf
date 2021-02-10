/*
 * Copyright (c) 2021 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test.endtoend;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.NcmountService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(immediate = true)
public class EndtoendTestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EndtoendTestService.class);
    private final RpcProviderService rpcProviderService;
    private final MountPointService mountPointService;

    @Inject
    @Activate
    public EndtoendTestService(final @Reference RpcProviderService rpcProviderService,
                               final @Reference MountPointService mountPointService) {
        this.rpcProviderService = rpcProviderService;
        this.mountPointService = mountPointService;
        rpcProviderService.registerRpcImplementation(NcmountService.class,
                new NcmountServiceImpl(mountPointService));
    }
}
