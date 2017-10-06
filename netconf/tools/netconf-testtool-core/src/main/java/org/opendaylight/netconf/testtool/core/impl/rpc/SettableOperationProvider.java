/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.testtool.core.impl.rpc;

import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;

import java.util.Collections;
import java.util.Set;

public class SettableOperationProvider implements NetconfOperationServiceFactory {

    private final RpcMapping rpcMapping;

    public SettableOperationProvider(RpcMapping rpcMapping) {
        this.rpcMapping = rpcMapping;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Collections.emptySet();
    }

    @Override
    public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
        return () -> {
            //no op
        };
    }

    @Override
    public NetconfOperationService createService(final String netconfSessionIdForReporting) {
        return new SettableOperationService(rpcMapping);
    }

    private static class SettableOperationService implements NetconfOperationService {

        private final SettableRpc rpc;

        private SettableOperationService(RpcMapping rpcMapping) {
            this.rpc = new SettableRpc(rpcMapping);
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            return Collections.singleton(rpc);
        }

        @Override
        public void close() {
            // no op
        }
    }
}
