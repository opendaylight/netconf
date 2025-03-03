/*
 * Copyright (c) 2021 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test.endtoend;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ListNodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ListNodesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ShowNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ShowNodeOutput;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Singleton
@Component(immediate = true)
public final class EndtoendTestService implements AutoCloseable {
    private final Registration registration;

    @Inject
    @Activate
    public EndtoendTestService(final @Reference RpcProviderService rpcProviderService,
            final @Reference MountPointService mountPointService) {
        registration = rpcProviderService.registerRpcImplementations(
            new WriteRoutesImpl(mountPointService),
            (ShowNode) input -> RpcResultBuilder.<ShowNodeOutput>failed()
                .withError(ErrorType.APPLICATION, "Not implemented")
                .buildFuture(),
            (ListNodes) input -> RpcResultBuilder.<ListNodesOutput>failed()
                .withError(ErrorType.APPLICATION, "Not implemented")
                .buildFuture());
    }

    @Override
    @Deactivate
    @PreDestroy
    public void close() {
        registration.close();
    }

}
