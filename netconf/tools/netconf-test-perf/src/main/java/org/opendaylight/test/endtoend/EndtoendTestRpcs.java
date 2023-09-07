/*
 * Copyright (c) 2021 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test.endtoend;

import com.google.common.collect.ImmutableClassToInstanceMap;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ListNodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ShowNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutes;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.Rpc;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Singleton
@Component(immediate = true)
public class EndtoendTestRpcs implements AutoCloseable {
    private final Registration registration;

    @Inject
    @Activate
    public EndtoendTestRpcs(final @Reference RpcProviderService rpcProviderService,
                               final @Reference MountPointService mountPointService) {
        final var service = new NcmountRpcs(mountPointService);
        registration = rpcProviderService.registerRpcImplementations(
            ImmutableClassToInstanceMap.<Rpc<?, ?>>builder()
                .put(WriteRoutes.class, service::writeRoutes)
                .put(ShowNode.class, service::showNode)
                .put(ListNodes.class, service::listNodes)
                .build());
    }

    @Override
    @Deactivate
    @PreDestroy
    public void close() {
        registration.close();
    }
}
