/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test;

import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ListNodesInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ListNodesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.NcmountService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ShowNodeInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ShowNodeOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutesInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutesOutput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NcmountManager {

    private static final Logger LOG = LoggerFactory.getLogger(NcmountManager.class);

    private final RpcProviderService rpcProviderService;

    public NcmountManager(final @Reference RpcProviderService rpcProviderService) {
        this.rpcProviderService = rpcProviderService;
        rpcProviderService.registerRpcImplementation(NcmountService.class, new DummyImplementation());
    }

    private static class DummyImplementation implements NcmountService {

        public DummyImplementation() {
        }

        @Override
        public ListenableFuture<RpcResult<WriteRoutesOutput>> writeRoutes(WriteRoutesInput input) {
            return null;
        }

        @Override
        public ListenableFuture<RpcResult<ShowNodeOutput>> showNode(ShowNodeInput input) {
            return null;
        }

        @Override
        public ListenableFuture<RpcResult<ListNodesOutput>> listNodes(ListNodesInput input) {
            return null;
        }
    }

}
